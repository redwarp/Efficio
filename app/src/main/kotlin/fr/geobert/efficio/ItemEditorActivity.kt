package fr.geobert.efficio

import android.app.Activity
import android.app.Fragment
import android.app.LoaderManager
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.MenuItem
import fr.geobert.efficio.data.Department
import fr.geobert.efficio.data.Task
import fr.geobert.efficio.db.TaskTable
import fr.geobert.efficio.misc.EditorToolbarTrait
import kotlinx.android.synthetic.main.item_editor.*
import kotlin.properties.Delegates

class ItemEditorActivity : BaseActivity(), DepartmentManager.DepartmentChoiceListener,
        LoaderManager.LoaderCallbacks<Cursor>, EditorToolbarTrait {
    private var task: Task by Delegates.notNull()
    private var origTask: Task by Delegates.notNull()
    private var depManager: DepartmentManager by Delegates.notNull()
    private var cursorLoader: CursorLoader? = null

    private val GET_TASK = 300

    companion object {
        fun callMe(ctx: Fragment, storeId: Long, task: Task) {
            val i = Intent(ctx.activity, ItemEditorActivity::class.java)
            i.putExtra("storeId", storeId)
            i.putExtra("taskId", task.id)
            ctx.startActivityForResult(i, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.item_editor)
        initToolbar(this)
        setTitle(R.string.change_item_name_and_dep)
        val extras = intent.extras
        depManager = DepartmentManager(this, findViewById(R.id.department_layout),
                extras.getLong("storeId"), this)
        depManager.request()
        fetchTask()
    }

    private fun fetchTask() {
        if (cursorLoader == null)
            loaderManager.initLoader(GET_TASK, intent.extras, this)
        else
            loaderManager.restartLoader(GET_TASK, intent.extras, this)
    }

    private fun onOkClicked() {
        task.item.name = item_name_edt.text.trim().toString()
        val needUpdate = !task.isEquals(origTask)
        if (needUpdate) {
            TaskTable.updateTask(this, task)
            setResult(Activity.RESULT_OK)
        } else setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDepartmentChosen(d: Department) {
        task.item.department = d
        setDepName()
    }

    private fun setDepName() {
        dep_name.text =
                getString(R.string.current_department).format(task.item.department.name)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.confirm -> {
                onOkClicked()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateLoader(p0: Int, b: Bundle): Loader<Cursor>? {
        cursorLoader = TaskTable.getTaskByIdLoader(this, b.getLong("taskId"))
        return cursorLoader
    }

    override fun onLoadFinished(p0: Loader<Cursor>?, data: Cursor) {
        if (data.count > 0) {
            data.moveToFirst()
            task = Task(data)
            origTask = Task(task)
            item_name_edt.text = SpannableStringBuilder(task.item.name)
            setDepName()
        } else {
            // todo should not happen
        }
    }

    override fun onLoaderReset(p0: Loader<Cursor>?) {
        // todo
    }
}