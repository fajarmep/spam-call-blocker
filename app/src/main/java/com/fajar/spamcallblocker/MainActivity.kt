package com.fajar.spamcallblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.fajar.spamcallblocker.data.BlockedCall
import com.fajar.spamcallblocker.data.DatabaseHelper
import com.fajar.spamcallblocker.data.PreferenceManager
import com.fajar.spamcallblocker.data.RuleType
import com.fajar.spamcallblocker.databinding.ActivityMainBinding
import com.fajar.spamcallblocker.ui.HistoryAdapter
import com.fajar.spamcallblocker.ui.RulesAdapter
import com.fajar.spamcallblocker.ui.WhitelistAdapter
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var prefs: PreferenceManager

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var whitelistAdapter: WhitelistAdapter
    private lateinit var rulesAdapter: RulesAdapter

    private var allHistory: List<BlockedCall> = emptyList()

    private val roleRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateRoleStatus()
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Contact permission granted.", Toast.LENGTH_SHORT).show()
                binding.switchAllowContacts.isChecked = true
                prefs.isAllowAllContactsEnabled = true
            } else {
                Toast.makeText(this, "Contact permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        prefs = PreferenceManager(this)

        initViews()
        setupTabs()
        setupHistoryTab()
        setupWhitelistTab()
        setupRulesTab()
        setupSettingsTab()

        checkRequiredPermissions()
        updateRoleStatus()
    }

    override fun onResume() {
        super.onResume()
        updateRoleStatus()
        refreshCurrentTab()
    }

    private fun initViews() {
        binding.btnHeaderAction.setOnClickListener { requestCallScreeningRole() }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                hideKeyboard()
                when (tab?.position) {
                    0 -> showTab(binding.layoutHistory) { refreshHistory() }
                    1 -> showTab(binding.layoutWhitelist) { refreshWhitelist() }
                    2 -> showTab(binding.layoutRules) { refreshRules() }
                    3 -> showTab(binding.layoutSettings) { refreshSettingsUI() }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> refreshHistory()
                    1 -> refreshWhitelist()
                    2 -> refreshRules()
                    3 -> refreshSettingsUI()
                }
            }
        })
    }

    private fun showTab(activeView: View, onShown: () -> Unit) {
        binding.layoutHistory.visibility = if (activeView == binding.layoutHistory) View.VISIBLE else View.GONE
        binding.layoutWhitelist.visibility = if (activeView == binding.layoutWhitelist) View.VISIBLE else View.GONE
        binding.layoutRules.visibility = if (activeView == binding.layoutRules) View.VISIBLE else View.GONE
        binding.layoutSettings.visibility = if (activeView == binding.layoutSettings) View.VISIBLE else View.GONE
        onShown()
    }

    private fun refreshCurrentTab() {
        when (binding.tabLayout.selectedTabPosition) {
            0 -> refreshHistory()
            1 -> refreshWhitelist()
            2 -> refreshRules()
            3 -> refreshSettingsUI()
        }
    }

    // --- History ---
    private fun setupHistoryTab() {
        historyAdapter = HistoryAdapter(
            items = emptyList(),
            onUnblockClick = { handleUnblock(it) },
            onWhitelistClick = { handleWhitelistFromHistory(it) },
            onLongClick = { copyToClipboard(it.phoneNumber) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear All History")
                .setMessage("Delete all blocked call records?")
                .setPositiveButton("Delete") { _, _ ->
                    dbHelper.clearHistory()
                    refreshHistory()
                    Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnExportHistory.setOnClickListener { shareHistory() }

        // Search filter
        binding.etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterHistory(s?.toString() ?: "")
            }
        })

        refreshHistory()
    }

    private fun refreshHistory() {
        allHistory = dbHelper.getAllHistory()
        val query = binding.etHistorySearch.text?.toString() ?: ""
        filterHistory(query)
    }

    private fun filterHistory(query: String) {
        val filtered = if (query.isBlank()) allHistory
        else allHistory.filter { it.phoneNumber.contains(query, ignoreCase = true) || it.reason.contains(query, ignoreCase = true) }
        historyAdapter.updateData(filtered)
        binding.tvHistoryCount.text = "${filtered.size} Blocked Calls"
        binding.tvEmptyHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun handleUnblock(item: BlockedCall) {
        // Bug fix: only delete history entry, never delete rules
        dbHelper.deleteHistoryItem(item.id)
        refreshHistory()
        Toast.makeText(this, "${item.phoneNumber} removed from history.", Toast.LENGTH_SHORT).show()
    }

    private fun handleWhitelistFromHistory(item: BlockedCall) {
        val success = dbHelper.insertWhitelist(item.phoneNumber, "From History")
        if (success) {
            dbHelper.deleteHistoryItem(item.id)
            refreshHistory()
            Toast.makeText(this, "${item.phoneNumber} added to Whitelist.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", text))
        Toast.makeText(this, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    private fun shareHistory() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val lines = allHistory.joinToString("\n") { "${it.phoneNumber} — ${dateFormat.format(Date(it.timestamp))} — ${it.reason}" }
        if (lines.isBlank()) {
            Toast.makeText(this, "No history to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Blocked Call Log")
            putExtra(Intent.EXTRA_TEXT, "Blocked Call Log:\n\n$lines")
        }
        startActivity(Intent.createChooser(intent, "Share Blocked Call Log"))
    }

    // --- Whitelist ---
    private fun setupWhitelistTab() {
        whitelistAdapter = WhitelistAdapter(
            items = emptyList(),
            onDeleteClick = { item ->
                dbHelper.deleteWhitelistItem(item.id)
                refreshWhitelist()
                Toast.makeText(this, "Removed from Whitelist.", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvWhitelist.layoutManager = LinearLayoutManager(this)
        binding.rvWhitelist.adapter = whitelistAdapter

        binding.switchAllowContacts.isChecked = prefs.isAllowAllContactsEnabled
        binding.switchAllowContacts.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            prefs.isAllowAllContactsEnabled = isChecked
        }

        binding.btnAddWhitelist.setOnClickListener {
            val phone = binding.etWhitelistNumber.text.toString().trim()
            val note = binding.etWhitelistNote.text.toString().trim()
            if (phone.isEmpty()) {
                binding.etWhitelistNumber.error = "Phone number required"
                return@setOnClickListener
            }
            val success = dbHelper.insertWhitelist(phone, note)
            if (success) {
                binding.etWhitelistNumber.text?.clear()
                binding.etWhitelistNote.text?.clear()
                hideKeyboard()
                refreshWhitelist()
                Toast.makeText(this, "Added to Whitelist.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to add number.", Toast.LENGTH_SHORT).show()
            }
        }

        refreshWhitelist()
    }

    private fun refreshWhitelist() {
        val list = dbHelper.getAllWhitelist()
        whitelistAdapter.updateData(list)
        binding.tvWhitelistCount.text = "Whitelist Numbers (${list.size})"
        binding.tvEmptyWhitelist.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvWhitelist.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- Rules ---
    private fun setupRulesTab() {
        rulesAdapter = RulesAdapter(
            items = emptyList(),
            onDeleteClick = { rule ->
                dbHelper.deleteBlockRule(rule.id)
                refreshRules()
                Toast.makeText(this, "Block rule deleted.", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = rulesAdapter

        binding.btnAddRule.setOnClickListener {
            val pattern = binding.etRulePattern.text.toString().trim()
            val note = binding.etRuleNote.text.toString().trim()
            if (pattern.isEmpty()) {
                binding.etRulePattern.error = "Pattern required"
                return@setOnClickListener
            }

            val ruleType = when (binding.rgRuleType.checkedRadioButtonId) {
                R.id.rbStartsWith -> RuleType.STARTS_WITH
                R.id.rbContains -> RuleType.CONTAINS
                R.id.rbEndsWith -> RuleType.ENDS_WITH
                R.id.rbExact -> RuleType.EXACT
                else -> RuleType.STARTS_WITH
            }

            val success = dbHelper.insertBlockRule(ruleType, pattern, note)
            if (success) {
                binding.etRulePattern.text?.clear()
                binding.etRuleNote.text?.clear()
                hideKeyboard()
                refreshRules()
                Toast.makeText(this, "Rule saved.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save rule.", Toast.LENGTH_SHORT).show()
            }
        }

        refreshRules()
    }

    private fun refreshRules() {
        val list = dbHelper.getAllRules()
        rulesAdapter.updateData(list)
        binding.tvRulesCount.text = "Active Block Rules (${list.size})"
        binding.tvEmptyRules.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRules.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- Settings ---
    private fun setupSettingsTab() {
        binding.switchMaster.isChecked = prefs.isServiceEnabled
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.isServiceEnabled = isChecked
            updateRoleStatus()
        }

        binding.switchBlockPrivate.isChecked = prefs.isBlockUnknownPrivateEnabled
        binding.switchBlockPrivate.setOnCheckedChangeListener { _, isChecked ->
            prefs.isBlockUnknownPrivateEnabled = isChecked
        }

        binding.switchBlockAll.isChecked = prefs.isBlockAllCallsEnabled
        binding.switchBlockAll.setOnCheckedChangeListener { _, isChecked ->
            prefs.isBlockAllCallsEnabled = isChecked
        }

        binding.switchNotification.isChecked = prefs.isNotificationEnabled
        binding.switchNotification.setOnCheckedChangeListener { _, isChecked ->
            prefs.isNotificationEnabled = isChecked
        }

        binding.btnRequestRole.setOnClickListener { requestCallScreeningRole() }

        binding.btnCheckContactPermission.setOnClickListener {
            requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }

        binding.btnApplyCommonSpamPresets.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Apply Common Spam Presets")
                .setMessage("Add filters for 021 (telemarketing), +1 (VoIP bots), 140 and 150 (corporate)?")
                .setPositiveButton("Add") { _, _ ->
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "021", "Local Telemarketing")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "+1", "Foreign Bot (US/CA)")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "140", "Bank/Corporate Promo")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "150", "Corporate Call Center")
                    refreshRules()
                    Toast.makeText(this, "Presets added.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshSettingsUI() {
        binding.switchMaster.isChecked = prefs.isServiceEnabled
        binding.switchBlockPrivate.isChecked = prefs.isBlockUnknownPrivateEnabled
        binding.switchBlockAll.isChecked = prefs.isBlockAllCallsEnabled
        binding.switchNotification.isChecked = prefs.isNotificationEnabled
        binding.switchAllowContacts.isChecked = prefs.isAllowAllContactsEnabled

        // Stats
        val histCount = dbHelper.getHistoryCount()
        val ruleCount = dbHelper.getRuleCount()
        val whiteCount = dbHelper.getWhitelistCount()
        binding.tvStatsContent.text = "Blocked calls:     $histCount\nActive rules:      $ruleCount\nWhitelist entries: $whiteCount"
    }

    // --- Role & Permissions ---
    private fun updateRoleStatus() {
        val isHeld = isCallScreeningRoleHeld()
        if (isHeld) {
            binding.tvHeaderStatus.text = "Call Screening: ACTIVE"
            binding.tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnHeaderAction.text = "Active"
            binding.btnHeaderAction.isEnabled = false
            binding.tvSettingsRoleStatus.text = "Status: Call Screening permission granted."
            binding.btnRequestRole.text = "Permission Granted"
            binding.btnRequestRole.isEnabled = false
        } else {
            binding.tvHeaderStatus.text = "Call Screening Not Authorized"
            binding.tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
            binding.viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            binding.btnHeaderAction.text = "Activate"
            binding.btnHeaderAction.isEnabled = true
            binding.tvSettingsRoleStatus.text = "App needs Call Screening permission for automatic blocking."
            binding.btnRequestRole.text = "Grant Permission"
            binding.btnRequestRole.isEnabled = true
        }
    }

    private fun isCallScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
        }
        return true
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    return
                }
            }
        }
        Toast.makeText(this, "Call Screening permission active.", Toast.LENGTH_SHORT).show()
    }

    private fun checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: binding.root
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
