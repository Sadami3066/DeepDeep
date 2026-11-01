package io.github.whywood.deepdeep.contract

import io.github.whywood.deepdeep.test.R
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ToolContractFixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val input = EditText(this).apply { contentDescription = getString(R.string.contract_input); setText(R.string.contract_initial) }
        val result = TextView(this).apply { setText(R.string.contract_ready); textSize = 20f }
        layout.addView(input)
        layout.addView(Button(this).apply { setText(R.string.contract_confirm); setOnClickListener { result.text = getString(R.string.contract_confirmed, input.text) } })
        layout.addView(result)
        repeat(35) { index -> layout.addView(TextView(this).apply { text = getString(R.string.contract_row, index); textSize = 24f; setPadding(8, 24, 8, 24) }) }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
