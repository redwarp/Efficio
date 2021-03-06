package fr.geobert.efficio


import android.app.*
import android.content.*
import android.database.Cursor
import android.os.Bundle
import android.support.v7.widget.*
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.*
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import fr.geobert.efficio.adapter.*
import fr.geobert.efficio.data.*
import fr.geobert.efficio.db.*
import fr.geobert.efficio.dialog.*
import fr.geobert.efficio.misc.*
import kotlinx.android.synthetic.main.item_list_fragment.*
import java.util.*

class TaskListFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor>, TextWatcher,
        DepartmentManager.DepartmentChoiceListener, TaskViewHolder.TaskViewHolderListener,
        RefreshInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val TAG = "TaskListFragment"

    var lastStoreId: Long = 1 // todo get it from prefs
    var currentStore: Store? = null
    var cursorLoader: CursorLoader? = null
    var taskAdapter: TaskAdapter? = null
    var tasksList: MutableList<Task> = LinkedList()
    val refreshReceiver = OnRefreshReceiver(this)
    val mainActivity by lazy { activity as MainActivity }

    private val header = Task()

    // this manage drag and swipe on tasks
    private val dragSwipeHlp by lazy { TaskDragSwipeHelper(this, tasksList, taskAdapter!!) }
    private val taskItemTouchHlp by lazy { ItemTouchHelper(dragSwipeHlp) }

    fun updateTasksList(needAdapterSort: Boolean) {
        val f = quick_add_text.text.trim().toString()
        if (needAdapterSort || !f.isEmpty()) {
            val l = if (!f.isEmpty()) filter(tasksList, f) else tasksList
            taskAdapter!!.animateTo(l)
            tasks_list.post {
                tasks_list.invalidateItemDecorations()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.item_list_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        tasks_list.layoutManager = LinearLayoutManager(this.activity)
        tasks_list.itemAnimator = DefaultItemAnimator()
        tasks_list.setHasFixedSize(true)
        tasks_list.addItemDecoration(TopBottomSpaceItemDecoration(10))

        quick_add_btn.setOnClickListener {
            onAddTaskClicked()
        }

        quick_add_text.addTextChangedListener(this)
        quick_add_text.setOnEditorActionListener { textView, i, keyEvent ->
            onEditorAction(i)
        }

        fetchStore(this, lastStoreId)

        activity.registerReceiver(refreshReceiver, IntentFilter(OnRefreshReceiver.REFRESH_ACTION))
        mainActivity.prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        quick_add_btn.isEnabled = quick_add_text.text.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity.unregisterReceiver(refreshReceiver)
    }

    private fun onEditorAction(actionId: Int): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            onAddTaskClicked()
            return true
        }
        return false
    }

    private fun onAddTaskClicked() {
        val (t, pos) = taskAdapter!!.getTaskByName(quick_add_text.text.trim().toString())
        if (t != null) {
            t.isDone = false
            taskAdapter!!.notifyItemChanged(pos)
        } else {
            // case item does not exists yet
            createNewTask()
        }
    }

    private fun createNewTask() {
        val d = DepartmentChoiceDialog.newInstance(lastStoreId)
        d.setTargetFragment(this, CREATE_TASK)
        d.show(fragmentManager, "DepChoiceDialog")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            0 -> onItemEditFinished(resultCode == Activity.RESULT_OK, data)
            1 -> onDepEditFinished(resultCode == Activity.RESULT_OK)
        }

    }

    private fun onDepEditFinished(needUpdate: Boolean) {
        if (needUpdate) {
            fetchStore(this, lastStoreId)
            updateWidgets()
        }
    }

    fun onItemEditFinished(needUpdate: Boolean, data: Intent?) {
        if (needUpdate) {
            quick_add_text.text.clear()
            val needWeightUpdate =
                    data?.getBooleanExtra(ItemEditorActivity.NEED_WEIGHT_UPDATE, false) ?: false
            val d = data
            if (needWeightUpdate && d != null) {
                val task = taskAdapter!!.getTaskById(d.getLongExtra("taskId", 0))
                if (task != null) {
                    val max = findMaxWeightForDepartment(task.item.department)
                    task.item.weight = max + 1.0
                    ItemWeightTable.updateWeight(activity, task.item)
                }
            }
            fetchStore(this, lastStoreId)
            updateWidgets()
        }
    }

//
// DepartmentChoiceListener
//

    override fun onDepartmentChosen(d: Department) {
        Log.d(TAG, "onDepartmentChosen : ${d.name}")
        // we choose a department, so the task does not exist
        val maxWeightForDep = findMaxWeightForDepartment(d)
        val i = Item(quick_add_text.text.trim().toString(), d, maxWeightForDep + 1.0)
        i.id = ItemTable.create(activity, i)
        if (i.id > 0) {
            // add to adapter, but need to find the right position
            val t = Task(i)
            tasksList.add(t)

            sort()

            taskAdapter!!.animateTo(tasksList)
            quick_add_text.text.clear()

            if (ItemWeightTable.create(activity, i, lastStoreId) > 0) {
                if (ItemDepTable.create(activity, i, lastStoreId) > 0) {
                    if (TaskTable.create(activity, t, lastStoreId) > 0) {
                        updateWidgets()
                    } else {
                        Log.e(TAG, "error on creating task")
                    }
                }
            } else {
                Log.e(TAG, "error on item weight creation")
            }
        } else {
            Log.e(TAG, "error on item creation")
        }

    }

    private fun sort() {
        if (mainActivity.prefs.getBoolean("invert_list_pref", false)) tasksList.sortDescending()
        else tasksList.sort()
    }

    internal fun updateWidgets() {
        (activity as MainActivity).updateWidgets()
    }

    private fun findMaxWeightForDepartment(d: Department): Double {
        val filteredByDep = tasksList.filter { t -> t.item.department.id == d.id }
        return if (filteredByDep.isNotEmpty()) filteredByDep.last().item.weight else 0.0
    }

    override fun onDoneStateChanged(task: Task) {
        TaskTable.updateDoneState(activity, task.id, task.isDone)
        sort()
        addHeaderIfNeeded(tasksList)
        val invertedList = mainActivity.prefs.getBoolean("invert_list_pref", false)
        val layMan = (tasks_list.layoutManager as LinearLayoutManager)
        val pos = if (invertedList)
            if (task.isDone) layMan.findLastVisibleItemPosition()
            else layMan.findFirstVisibleItemPosition()
        else
            if (task.isDone) layMan.findFirstVisibleItemPosition()
            else layMan.findLastVisibleItemPosition()
        taskAdapter!!.animateTo(tasksList)
        tasks_list.scrollToPosition(pos)
        tasks_list.post { tasks_list.invalidateItemDecorations() }
        updateWidgets()
    }

    override fun onItemClicked(task: Task) {
        ItemEditorActivity.callMe(this, lastStoreId, task)
    }

    override fun onQtyClicked(task: Task) {
        QuantityDialog.newInstance(task).show(fragmentManager, "QtyEdit")
    }

    //
    // TextWatcher
    //
    override fun afterTextChanged(s: Editable) {
        quick_add_btn.isEnabled = s.trim().isNotEmpty()
        if (tasksList.count() > 0) {
            val filteredList = filter(tasksList, s.toString())
            addHeaderIfNeeded(filteredList)
            taskAdapter!!.animateTo(filteredList)
            tasks_list.post {
                tasks_list.invalidateItemDecorations()
            }
            tasks_list.scrollToPosition(0)
        }
    }

    private fun filter(list: MutableList<Task>, s: String): MutableList<Task> {
        val f = s.toLowerCase()
        val filtered = list.filterTo(LinkedList<Task>()) {
            (it.type == TaskAdapter.VIEW_TYPES.Normal && it.item.normName().toLowerCase().contains(f)) ||
                    it.type == TaskAdapter.VIEW_TYPES.Header
        }
        return filtered
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // nothing
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // nothing
    }

    override fun onRefresh(intent: Intent) {
        val extras = intent.extras
        val storeId = extras.getLong("storeId", -1)
        val newStoreId = extras.getLong("newStoreId", -1)
        val taskId = extras.getLong("taskId", -1)
        if (newStoreId > 0) lastStoreId = newStoreId
        if (taskId > -1) { // for the moment, this is only when a quantity has been edited via dialog
            taskAdapter!!.refreshTaskFromDB(activity, taskId)
        } else if (storeId == lastStoreId || storeId < 0L) {
            fetchStore(this, lastStoreId)
        }
    }

    //
    // Database operations
    //

    fun fetchStore(ctx: Fragment, storeId: Long) {
        val b = Bundle()
        b.putLong("storeId", storeId)
        if (cursorLoader == null) {
            ctx.loaderManager.initLoader(GET_TASKS_OF_STORE, b, this)
        } else {
            ctx.loaderManager.restartLoader(GET_TASKS_OF_STORE, b, this)
        }
    }

    override fun onCreateLoader(i: Int, bundle: Bundle?): Loader<Cursor>? {
        cursorLoader = when (i) {
            GET_TASKS_OF_STORE -> TaskTable.getAllTasksForStoreLoader(this.activity,
                    bundle?.getLong("storeId") ?: 0)
            else -> null
        }
        return cursorLoader
    }

    private fun addHeaderIfNeeded(list: MutableList<Task>) {
        var lastState: Boolean? = null
        var addPos: Int? = null
        list.remove(header)
        val invertedList = mainActivity.prefs.getBoolean("invert_list_pref", false)
        for (t in list) {
            if (t.isDone && lastState == null) {
                addPos = if (invertedList) list.size else 0
            } else {
                if (lastState != null && lastState != t.isDone) {
                    addPos = list.indexOf(t)
                    break
                }
            }
            lastState = t.isDone
        }

        if (addPos != null) {
            list.add(addPos, header)
        }
    }

    override fun onLoadFinished(cursorLoader: Loader<Cursor>, cursor: Cursor) {
        when (cursorLoader.id) {
            GET_TASKS_OF_STORE -> {
                if (cursor.count > 0) {
                    tasksList = if (mainActivity.prefs.getBoolean("invert_list_pref", false))
                        cursor.mapInvert(::Task) else cursor.map(::Task)
                    tasks_list.visibility = View.VISIBLE
                    empty_text.visibility = View.GONE
                } else {
                    tasksList = LinkedList<Task>()
                    tasks_list.visibility = View.GONE
                    empty_text.visibility = View.VISIBLE
                }
                addHeaderIfNeeded(tasksList)
                if (taskAdapter == null)
                    taskAdapter = TaskAdapter(tasksList, this, mainActivity.prefs)
                else
                    taskAdapter!!.animateTo(tasksList)
                dragSwipeHlp.tasksList = tasksList
                tasks_list.adapter = taskAdapter
                taskItemTouchHlp.attachToRecyclerView(tasks_list)
            }
            else -> throw IllegalArgumentException("Unknown cursorLoader id")
        }
    }

    override fun onLoaderReset(cursorLoader: Loader<Cursor>?) {
        currentStore = null
        cursorLoader?.reset()
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
        when (key) {
            "invert_checkbox_pref", "invert_list_pref" -> fetchStore(this, lastStoreId)
        }
    }
}