package com.example.bitfit

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bitfit.databinding.FragmentTrackerBinding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class TrackerFragment : Fragment() {
    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private var database: WaterDatabase? = null
    private lateinit var adapter: EntryAdapter
    private var selectedMonth = YearMonth.now()
    private var selectedDate = LocalDate.now()
    private val dbDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = WaterDatabase(requireContext()).also { it.seedDemoMonthIfEmpty() }
        adapter = EntryAdapter()
        binding.entries.layoutManager = LinearLayoutManager(requireContext())
        binding.entries.isNestedScrollingEnabled = false
        binding.entries.adapter = adapter
        binding.calendarView.setShowWeekNumber(false)
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            val newMonth = YearMonth.from(selectedDate)
            if (newMonth != selectedMonth) {
                selectedMonth = newMonth
                refreshMonth()
            } else {
                updateSelectedDateLabel()
            }
        }
        binding.addEntryButton.setOnClickListener { showCreateEntryDialog() }
        refreshMonth()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && database != null) refreshMonth()
    }

    override fun onDestroyView() {
        binding.entries.adapter = null
        database?.close()
        database = null
        _binding = null
        super.onDestroyView()
    }

    private fun refreshMonth() {
        val db = database ?: return
        val entries = db.getEntriesForMonth(selectedMonth)
        binding.monthTitle.text = selectedMonth.atDay(1).format(monthFormatter)
        binding.monthEntryCount.text = if (entries.isEmpty()) "No entries" else "${entries.size} days logged"
        adapter.submitList(entries)
        binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.entries.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        updateSelectedDateLabel()

        if (entries.isEmpty()) {
            binding.averageValue.text = "— oz"
            binding.trendValue.text = "Log daily intake to build your monthly trend."
            binding.trendChart.setEntries(emptyList(), selectedMonth)
            return
        }

        val average = entries.map { it.ounces }.average()
        binding.averageValue.text = String.format(Locale.getDefault(), "%.0f oz", average)
        val chronological = entries.sortedBy { it.date }
        val change = chronological.last().ounces - chronological.first().ounces
        binding.trendValue.text = when {
            chronological.size < 2 -> "Add another entry to compare your month"
            change > 0 -> "Up $change oz from the first logged day this month"
            change < 0 -> "Down ${-change} oz from the first logged day this month"
            else -> "Same intake as your first logged day this month"
        }
        binding.trendChart.setEntries(entries, selectedMonth)
    }

    private fun updateSelectedDateLabel() {
        val entry = database?.getEntry(selectedDate.format(dbDate))
        binding.selectedDateValue.text = entry?.let {
            "${selectedDate.format(dayFormatter)} · ${it.ounces} oz${if (it.isDemo) " demo" else ""}"
        } ?: "${selectedDate.format(dayFormatter)} · no entry"
    }

    private fun showCreateEntryDialog() {
        val input = EditText(requireContext()).apply {
            hint = "e.g. 64"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setPadding(48, 24, 48, 24)
            database?.getEntry(selectedDate.format(dbDate))?.let { setText(it.ounces.toString()) }
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(requireContext()).apply {
                text = "Water intake for ${selectedDate.format(dayFormatter)} (fluid ounces)"
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink))
                setPadding(48, 24, 48, 0)
            })
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Daily water entry")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save entry", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val amount = input.text.toString().toDoubleOrNull()
                        if (amount == null || amount <= 0 || amount > 1000) {
                            input.error = "Enter an amount from 1 to 1,000 oz"
                            return@setOnClickListener
                        }
                        database?.saveEntry(selectedDate.format(dbDate), amount.toInt())
                        Toast.makeText(requireContext(), "Daily entry saved", Toast.LENGTH_SHORT).show()
                        refreshMonth()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }
}
