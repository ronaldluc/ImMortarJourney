package com.example.immortarjourney

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DividerItemDecoration



class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: BuildListAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buildList = arrayOf(
            BuildItem("Great Wall"),
            BuildItem("House"),
            BuildItem("Bath Tiling"),
            BuildItem("Snowman"))

        viewManager = LinearLayoutManager(this)
        viewAdapter = BuildListAdapter(buildList)

        recyclerView = findViewById<RecyclerView>(R.id.build_list)
        recyclerView.apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)

            // use a linear layout manager
            layoutManager = viewManager
            val dividerItemDecoration = DividerItemDecoration(
                recyclerView.context,
                viewManager.layoutDirection
            )
            recyclerView.addItemDecoration(dividerItemDecoration)

            // specify an viewAdapter (see also next example)
            adapter = viewAdapter

            viewAdapter.onItemClick = fun (pos: Int, _: View?) {
                val item = buildList[pos]
                val intent = Intent(context, PickPowder::class.java)
                startActivity(intent)
            }
        }

    }
}
