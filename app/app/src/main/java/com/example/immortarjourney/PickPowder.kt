package com.example.immortarjourney

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DividerItemDecoration



class PickPowder : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: BuildListAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_powder)

        val buildList = arrayOf(
            BuildItem("WPM"), // Water proofing membrane
            BuildItem("CTA"), // Ceramic tile adhesive
            BuildItem("SLC")) // Self leveling compound

        viewManager = LinearLayoutManager(this)
        viewAdapter = BuildListAdapter(buildList)

        recyclerView = findViewById(R.id.powder_list)
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
                val intent = Intent(context, Loading::class.java)
                startActivity(intent)
            }
        }

    }
}
