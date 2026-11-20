package com.stupidtree.hita.ui.resource

import android.content.Context
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.stupidtree.component.data.DataState
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hita.api.ResourceDataService
import com.stupidtree.hitax.data.model.resource.CourseReadmeData
import com.stupidtree.hitax.data.model.resource.CourseResourceItem

class ResourceUiPlugin : ComposeUiPlugin {
    override val id = "com.stupidtree.hita.ui.resource"
    override val displayName = "课程资源"
    override val requiresApi = 7

    override fun createView(context: Context, host: PluginUiHost): View = ComposeView(context).apply {
        setContent {
            MaterialTheme { ResourceScreen(host.service(ResourceDataService.KEY)) }
        }
    }
}

@Composable
private fun ResourceScreen(service: ResourceDataService?) {
    if (service == null) {
        Text("未安装 resource T1 core", modifier = Modifier.padding(24.dp))
        return
    }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CourseResourceItem>>(emptyList()) }
    var readme by remember { mutableStateOf<CourseReadmeData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(submitted) {
        readme = null; error = null; loading = true
        if (submitted.isBlank()) { results = emptyList(); loading = false; return@LaunchedEffect }
        service.searchCourses(submitted).observeOnce { state ->
            when (state.state) {
                DataState.STATE.SUCCESS -> results = state.data.orEmpty()
                DataState.STATE.FETCH_FAILED -> error = state.message ?: "检索失败"
                else -> Unit
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("课程名或代码") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Button(onClick = { submitted = query.trim() }, enabled = !loading) { Text("搜索") }
            if (loading) Text("加载中…", Modifier.padding(start = 12.dp))
        }
        error?.let { Text("错误：$it") }
        val current = readme
        if (current != null) {
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { readme = null }) { Text("返回") }
            }
            Text(current.markdown, Modifier.padding(top = 8.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            items(results, key = { it.repoName }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp).clickable {
                        service.getCourseReadme(item.repoName).observeOnce { state ->
                            if (state.state == DataState.STATE.SUCCESS) readme = state.data
                            else error = state.message ?: "读取失败"
                        }
                    }) {
                        Text(item.courseName, style = MaterialTheme.typography.subtitle1)
                        Text("${item.courseCode} · ${item.repoType}", style = MaterialTheme.typography.caption)
                        if (item.teachers.isNotEmpty()) Text(item.teachers.joinToString("、"), style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private fun <T> LiveData<T>.observeOnce(onChanged: (T) -> Unit) {
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            removeObserver(this)
            onChanged(value)
        }
    }
    observeForever(observer)
}
