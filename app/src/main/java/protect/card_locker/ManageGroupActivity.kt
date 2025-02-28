package protect.card_locker

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.ArrayList
import java.util.HashMap
import protect.card_locker.databinding.ActivityManageGroupBinding

// TODO: Workaround until `ManageGroupCursorAdapter` and `LoyaltyCardCursorAdapter` have been
//  converted to Kotlin.
interface CardAdapterCallbacks {
    fun onRowClicked(inputPosition: Int)
    fun onRowLongClicked(inputPosition: Int)
}

// TODO: Uncomment and use this once `ManageGroupCursorAdapter` and `LoyaltyCardCursorAdapter`
//  have been converted to Kotlin.
//class ManageGroupActivity : CatimaAppCompatActivity(), ManageGroupCursorAdapter.CardAdapterListener {

// TODO: Use this until `ManageGroupCursorAdapter` and `LoyaltyCardCursorAdapter` have been
//  converted to Kotlin.
class ManageGroupActivity : CatimaAppCompatActivity(), CardAdapterCallbacks {
    private lateinit var binding: ActivityManageGroupBinding
    private lateinit var mDatabase: SQLiteDatabase
    private lateinit var mAdapter: ManageGroupCursorAdapter

    private companion object {
        private const val SAVE_INSTANCE_ADAPTER_STATE = "adapterState"
        private const val SAVE_INSTANCE_CURRENT_GROUP_NAME = "currentGroupName"
    }

    var mGroup: Group? = null
    private lateinit var mCardList: RecyclerView
    private lateinit var noGroupCardsText: TextView
    private lateinit var mGroupNameText: EditText

    private var mGroupNameNotInUse: Boolean = true

    override fun onCreate(inputSavedInstanceState: Bundle?) {
        super.onCreate(inputSavedInstanceState)
        binding = ActivityManageGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        mDatabase = DBHelper(this).writableDatabase

        noGroupCardsText = binding.include.noGroupCardsText
        mCardList = binding.include.list
        val saveButton = binding.fabSave

        mGroupNameText = binding.editTextGroupName

        mGroupNameText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                mGroupNameNotInUse = true
                mGroupNameText.error = null
                val currentGroupName = mGroupNameText.text.toString().trim()
                if (currentGroupName.isEmpty()) {
                    mGroupNameText.error = resources.getText(R.string.group_name_is_empty)
                    return
                }
                if (mGroup?._id != currentGroupName) {
                    if (DBHelper.getGroup(mDatabase, currentGroupName) != null) {
                        mGroupNameNotInUse = false
                        mGroupNameText.error = resources.getText(R.string.group_name_already_in_use)
                    } else {
                        mGroupNameNotInUse = true
                    }
                }
            }
        })

        val intent = intent
        val groupId = intent.getStringExtra("group")
            ?: throw IllegalArgumentException("this activity expects a group loaded into it's intent")

        Log.d("groupId", "groupId: $groupId")
        mGroup = DBHelper.getGroup(mDatabase, groupId)
            ?: throw IllegalArgumentException("cannot load group $groupId from database")

        mGroupNameText.setText(mGroup?._id)
        setTitle(getString(R.string.editGroup, mGroup?._id))

        // TODO: Uncomment and use this once `ManageGroupCursorAdapter` and
        //  `LoyaltyCardCursorAdapter` have been converted to Kotlin.
//        mAdapter = ManageGroupCursorAdapter(this, null, this, mGroup, null)
//        mCardList.adapter = mAdapter

        // TODO: Use this until `ManageGroupCursorAdapter` and `LoyaltyCardCursorAdapter` have been
        //  converted to Kotlin.
        createAdapter()

        registerForContextMenu(mCardList)

        if (inputSavedInstanceState != null) {
            mAdapter.importInGroupState(integerArrayToAdapterState(inputSavedInstanceState.getIntegerArrayList(SAVE_INSTANCE_ADAPTER_STATE)))
            mGroupNameText.setText(inputSavedInstanceState.getString(SAVE_INSTANCE_CURRENT_GROUP_NAME))
        }

        enableToolbarBackButton()

        saveButton.setOnClickListener { v ->
            val currentGroupName = mGroupNameText.text.toString().trim()
            if (currentGroupName != mGroup?._id) {
                if (currentGroupName.isEmpty()) {
                    Toast.makeText(applicationContext, R.string.group_name_is_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!mGroupNameNotInUse) {
                    Toast.makeText(applicationContext, R.string.group_name_already_in_use, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            mAdapter.commitToDatabase()
            if (currentGroupName != mGroup?._id) {
                DBHelper.updateGroup(mDatabase, mGroup?._id ?: "", currentGroupName)
            }
            Toast.makeText(applicationContext, R.string.group_updated, Toast.LENGTH_SHORT).show()
            finish()
        }
        // this setText is here because content_main.xml is reused from main activity
        noGroupCardsText.setText(resources.getText(R.string.noGiftCardsGroup))
        updateLoyaltyCardList()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveWithoutSaving()
            }
        })
    }

    private fun adapterStateToIntegerArray(adapterState: HashMap<Int, Boolean>): ArrayList<Int> {
        val ret = ArrayList<Int>(adapterState.size * 2)
        for (entry in adapterState) {
            ret.add(entry.key)
            ret.add(if (entry.value) 1 else 0)
        }
        return ret
    }

    private fun integerArrayToAdapterState(input: ArrayList<Int>?): HashMap<Int, Boolean> {
        val ret = HashMap<Int, Boolean>()
        if (input == null || input.size % 2 != 0) {
            if (input != null) {
                throw RuntimeException("failed restoring adapterState from integer array list")
            }
            return ret
        }

        for (i in 0 until input.size step 2) {
            ret[input[i]] = input[i + 1] == 1
        }
        return ret
    }

    override fun onCreateOptionsMenu(inputMenu: Menu): Boolean {
        menuInflater.inflate(R.menu.card_details_menu, inputMenu)
        return super.onCreateOptionsMenu(inputMenu)
    }

    override fun onOptionsItemSelected(inputItem: MenuItem): Boolean {
        return when (inputItem.itemId) {
            R.id.action_display_options -> {
                mAdapter.showDisplayOptionsDialog()
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(inputItem)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putIntegerArrayList(SAVE_INSTANCE_ADAPTER_STATE, adapterStateToIntegerArray(mAdapter.exportInGroupState()))
        outState.putString(SAVE_INSTANCE_CURRENT_GROUP_NAME, mGroupNameText.text.toString())
    }

    private fun updateLoyaltyCardList() {
        mAdapter.swapCursor(DBHelper.getLoyaltyCardCursor(mDatabase))

        when (mAdapter.itemCount) {
            0 -> {
                mCardList.visibility = View.GONE
                noGroupCardsText.visibility = View.VISIBLE
            }
            else -> {
                mCardList.visibility = View.VISIBLE
                noGroupCardsText.visibility = View.GONE
            }
        }
    }

    private fun leaveWithoutSaving() {
        if (hasChanged()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.leaveWithoutSaveTitle)
                .setMessage(R.string.leaveWithoutSaveConfirmation)
                .setPositiveButton(R.string.confirm) { _, _ -> finish() }
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .create()
                .show()
        } else {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun hasChanged(): Boolean {
        return mAdapter.hasChanged() || mGroup?._id != mGroupNameText.text.toString().trim()
    }

    // TODO: This is a workaround until the `ManageGroupCursorAdapter` and `LoyaltyCardCursorAdapter`
    //  are converted to Kotlin.
    private fun createAdapter() {
        val adapterListener = object : LoyaltyCardCursorAdapter.CardAdapterListener {
            override fun onRowClicked(inputPosition: Int) {
                this@ManageGroupActivity.onRowClicked(inputPosition)
            }

            override fun onRowLongClicked(inputPosition: Int) {
                this@ManageGroupActivity.onRowLongClicked(inputPosition)
            }
        }

        mAdapter = ManageGroupCursorAdapter(this, null, adapterListener, mGroup, null)
        mCardList.adapter = mAdapter
    }

    override fun onRowLongClicked(inputPosition: Int) {
        mAdapter.toggleSelection(inputPosition)
    }

    override fun onRowClicked(inputPosition: Int) {
        mAdapter.toggleSelection(inputPosition)
    }
}
