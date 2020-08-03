package com.simprints.id.activities.settings.fingerselection

import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simprints.core.tools.activity.BaseSplitActivity
import com.simprints.id.Application
import com.simprints.id.R
import kotlinx.android.synthetic.main.activity_finger_selection.*
import javax.inject.Inject

class FingerSelectionActivity : BaseSplitActivity() {

    @Inject
    lateinit var viewModelFactory: FingerSelectionViewModelFactory
    private lateinit var viewModel: FingerSelectionViewModel

    private lateinit var fingerSelectionAdapter: FingerSelectionItemAdapter

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback =
            object : ItemTouchHelper.SimpleCallback(UP or DOWN or START or END, 0) {

                override fun onMove(recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    target: RecyclerView.ViewHolder): Boolean {
                    val adapter = recyclerView.adapter as FingerSelectionItemAdapter
                    val from = viewHolder.adapterPosition
                    val to = target.adapterPosition
                    // 2. Update the backing model. Custom implementation in
                    //    MainRecyclerViewAdapter. You need to implement
                    //    reordering of the backing model inside the method.
                    viewModel.moveItem(from, to)
                    // 3. Tell adapter to render the model update.
                    adapter.notifyItemMoved(from, to)
                    return true
                }
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                }
            }
        ItemTouchHelper(simpleItemTouchCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as Application).component.inject(this)
        setContentView(R.layout.activity_finger_selection)

        configureToolbar()

        viewModel = ViewModelProvider(this, viewModelFactory).get(FingerSelectionViewModel::class.java)

        initRecyclerView()
        initAddFingerButton()
        initResetButton()

        viewModel.items.observe(this, Observer { fingerSelectionAdapter.notifyDataSetChanged() })

        viewModel.start()
    }

    private fun configureToolbar() {
        setSupportActionBar(settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.finger_selection_activity_title)
    }

    private fun initRecyclerView() {
        fingerSelectionAdapter = FingerSelectionItemAdapter(this, viewModel)
        fingerSelectionRecyclerView.layoutManager = LinearLayoutManager(this)
        fingerSelectionRecyclerView.adapter = fingerSelectionAdapter
        itemTouchHelper.attachToRecyclerView(fingerSelectionRecyclerView)
    }

    private fun initAddFingerButton() {
        addFingerButton.setOnClickListener { viewModel.addNewFinger() }
    }

    private fun initResetButton() {
        resetButton.setOnClickListener { viewModel.resetFingerItems() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (viewModel.haveSettingsChanged()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.finger_selection_confirm_dialog_text))
                .setPositiveButton(getString(R.string.finger_selection_confirm_dialog_yes)) { _, _ ->
                    viewModel.savePreference()
                    super.onBackPressed()
                }
                .setNegativeButton(getString(R.string.finger_selection_confirm_dialog_no)) { _, _ -> super.onBackPressed() }
                .setCancelable(false).create().show()
        } else {
            super.onBackPressed()
        }
    }
}
