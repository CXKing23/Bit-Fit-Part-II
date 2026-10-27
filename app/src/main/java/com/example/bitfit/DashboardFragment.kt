package com.example.bitfit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bitfit.databinding.FragmentDashboardBinding
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private var database: WaterDatabase? = null
    private lateinit var recentAdapter: EntryAdapter
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = WaterDatabase(requireContext())
        recentAdapter = EntryAdapter()
        binding.recentEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recentEntries.isNestedScrollingEnabled = false
        binding.recentEntries.adapter = recentAdapter
        binding.openTrackerButton.setOnClickListener {
            findNavController().navigate(R.id.tracker_fragment)
        }
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && database != null) refreshDashboard()
    }

    override fun onDestroyView() {
        binding.recentEntries.adapter = null
        database?.close()
        database = null
        _binding = null
        super.onDestroyView()
    }

    private fun refreshDashboard() {
        val db = database ?: return
        val month = YearMonth.now()
        val entries = db.getEntriesForMonth(month)
        binding.dashboardMonth.text = month.atDay(1).format(monthFormatter)
        binding.daysLoggedValue.text = entries.size.toString()
        binding.totalValue.text = "${entries.sumOf { it.ounces }} oz"
        binding.averageValue.text = if (entries.isEmpty()) "— oz" else {
            String.format(Locale.getDefault(), "%.0f oz", entries.map { it.ounces }.average())
        }
        binding.bestDayValue.text = entries.maxByOrNull { it.ounces }?.let { "${it.ounces} oz" } ?: "—"
        binding.dashboardTrendChart.setEntries(entries, month)

        val recent = entries.take(5)
        binding.emptyRecentState.visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
        binding.recentEntries.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recentAdapter.submitList(recent)
    }
}
