package com.fajar.spamcallblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.fajar.spamcallblocker.data.BlockRule
import com.fajar.spamcallblocker.data.BlockedCall
import com.fajar.spamcallblocker.data.DatabaseHelper
import com.fajar.spamcallblocker.data.PreferenceManager
import com.fajar.spamcallblocker.data.RuleType
import com.fajar.spamcallblocker.data.WhitelistItem
import com.fajar.spamcallblocker.databinding.ActivityMainBinding
import com.fajar.spamcallblocker.ui.HistoryAdapter
import com.fajar.spamcallblocker.ui.RulesAdapter
import com.fajar.spamcallblocker.ui.WhitelistAdapter
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var prefs: PreferenceManager

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var whitelistAdapter: WhitelistAdapter
    private lateinit var rulesAdapter: RulesAdapter

    private val roleRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateRoleStatus()
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Izin kontak diberikan.", Toast.LENGTH_SHORT).show()
                binding.switchAllowContacts.isChecked = true
                prefs.isAllowAllContactsEnabled = true
            } else {
                Toast.makeText(this, "Izin kontak ditolak.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
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
        binding.btnHeaderAction.setOnClickListener {
            requestCallScreeningRole()
        }
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

    // --- Tab 1: History ---
    private fun setupHistoryTab() {
        historyAdapter = HistoryAdapter(
            items = emptyList(),
            onUnblockClick = { item -> handleUnblock(item) },
            onWhitelistClick = { item -> handleWhitelistFromHistory(item) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Semua Riwayat")
                .setMessage("Yakin ingin menghapus seluruh log panggilan yang diblokir?")
                .setPositiveButton("Hapus") { _, _ ->
                    dbHelper.clearHistory()
                    refreshHistory()
                    Toast.makeText(this, "Riwayat dibersihkan.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        refreshHistory()
    }

    private fun refreshHistory() {
        val list = dbHelper.getAllHistory()
        historyAdapter.updateData(list)
        binding.tvHistoryCount.text = "${list.size} Panggilan Diblokir"
        binding.tvEmptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun handleUnblock(item: BlockedCall) {
        val deletedRules = dbHelper.removeRuleByNumberMatch(item.phoneNumber)
        dbHelper.deleteHistoryItem(item.id)
        refreshHistory()
        val msg = if (deletedRules > 0) {
            "Nomor ${item.phoneNumber} di-unblock ($deletedRules aturan terkait dihapus)."
        } else {
            "Nomor ${item.phoneNumber} dihapus dari riwayat blokir."
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun handleWhitelistFromHistory(item: BlockedCall) {
        val success = dbHelper.insertWhitelist(item.phoneNumber, "Dari Riwayat Blokir")
        if (success) {
            dbHelper.deleteHistoryItem(item.id)
            refreshHistory()
            Toast.makeText(
                this,
                "Nomor ${item.phoneNumber} berhasil ditambahkan ke Whitelist.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --- Tab 2: Whitelist ---
    private fun setupWhitelistTab() {
        whitelistAdapter = WhitelistAdapter(
            items = emptyList(),
            onDeleteClick = { item ->
                dbHelper.deleteWhitelistItem(item.id)
                refreshWhitelist()
                Toast.makeText(this, "Nomor dihapus dari Whitelist.", Toast.LENGTH_SHORT).show()
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
                binding.etWhitelistNumber.error = "Nomor tidak boleh kosong"
                return@setOnClickListener
            }

            val success = dbHelper.insertWhitelist(phone, note)
            if (success) {
                binding.etWhitelistNumber.text?.clear()
                binding.etWhitelistNote.text?.clear()
                hideKeyboard()
                refreshWhitelist()
                Toast.makeText(this, "Nomor ditambahkan ke Whitelist.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal menambahkan nomor.", Toast.LENGTH_SHORT).show()
            }
        }

        refreshWhitelist()
    }

    private fun refreshWhitelist() {
        val list = dbHelper.getAllWhitelist()
        whitelistAdapter.updateData(list)
        binding.tvWhitelistCount.text = "Daftar Nomor Whitelist (${list.size})"
        binding.tvEmptyWhitelist.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvWhitelist.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- Tab 3: Rules ---
    private fun setupRulesTab() {
        rulesAdapter = RulesAdapter(
            items = emptyList(),
            onDeleteClick = { rule ->
                dbHelper.deleteBlockRule(rule.id)
                refreshRules()
                Toast.makeText(this, "Aturan blokir dihapus.", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = rulesAdapter

        binding.btnAddRule.setOnClickListener {
            val pattern = binding.etRulePattern.text.toString().trim()
            val note = binding.etRuleNote.text.toString().trim()
            if (pattern.isEmpty()) {
                binding.etRulePattern.error = "Pola nomor tidak boleh kosong"
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
                Toast.makeText(this, "Aturan baru berhasil disimpan.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal menyimpan aturan.", Toast.LENGTH_SHORT).show()
            }
        }

        refreshRules()
    }

    private fun refreshRules() {
        val list = dbHelper.getAllRules()
        rulesAdapter.updateData(list)
        binding.tvRulesCount.text = "Aturan Blokir Aktif (${list.size})"
        binding.tvEmptyRules.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRules.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- Tab 4: Settings ---
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

        binding.btnRequestRole.setOnClickListener {
            requestCallScreeningRole()
        }

        binding.btnCheckContactPermission.setOnClickListener {
            requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }

        binding.btnApplyCommonSpamPresets.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Terapkan Preset Spam Umum")
                .setMessage("Tambahkan filter awalan 021 (telemarketing), +1 (VoIP bot), dan awalan umum panggilan spam?")
                .setPositiveButton("Tambahkan") { _, _ ->
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "021", "Telemarketing Lokal")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "+1", "Bot Luar Negeri (US/CA)")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "140", "Promo Bank/Korporat")
                    dbHelper.insertBlockRule(RuleType.STARTS_WITH, "150", "Call Center Korporat")
                    refreshRules()
                    Toast.makeText(this, "Preset berhasil ditambahkan.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun refreshSettingsUI() {
        binding.switchMaster.isChecked = prefs.isServiceEnabled
        binding.switchBlockPrivate.isChecked = prefs.isBlockUnknownPrivateEnabled
        binding.switchBlockAll.isChecked = prefs.isBlockAllCallsEnabled
        binding.switchNotification.isChecked = prefs.isNotificationEnabled
        binding.switchAllowContacts.isChecked = prefs.isAllowAllContactsEnabled
    }

    // --- Call Screening Role & Permissions ---
    private fun updateRoleStatus() {
        val isHeld = isCallScreeningRoleHeld()
        if (isHeld) {
            binding.tvHeaderStatus.text = "Layanan Call Screening: AKTIF"
            binding.tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnHeaderAction.text = "Aktif"
            binding.btnHeaderAction.isEnabled = false
            binding.tvSettingsRoleStatus.text = "Status: Layanan Call Screening telah disetujui sistem Android."
            binding.btnRequestRole.text = "Izin Telah Diberikan"
            binding.btnRequestRole.isEnabled = false
        } else {
            binding.tvHeaderStatus.text = "Layanan Call Screening Belum Diizinkan"
            binding.tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
            binding.btnHeaderAction.text = "Aktifkan"
            binding.btnHeaderAction.isEnabled = true
            binding.tvSettingsRoleStatus.text = "Aplikasi memerlukan izin Call Screening agar sistem Android mengizinkan pemblokiran otomatis."
            binding.btnRequestRole.text = "Beri Izin Call Screening"
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
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    roleRequestLauncher.launch(intent)
                    return
                }
            }
        }
        Toast.makeText(this, "Izin Call Screening aktif.", Toast.LENGTH_SHORT).show()
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
