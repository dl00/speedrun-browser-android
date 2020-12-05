@file:Suppress("DEPRECATION")

package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.*

class TextInputDialog(
        ctx: Context,
        val title: String = "Entry:",
        var text: String = ""
) : AlertDialog(ctx, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert
    else THEME_DEVICE_DEFAULT_DARK), TextWatcher {

    private val titleTextView = TextView(context)

    private val editTextView = EditText(context)

    private val okButton = Button(context)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout.background = ColorDrawable(context.getColor(R.color.colorPrimary))
        }

        titleTextView.text = title

        editTextView.addTextChangedListener(this)

        layout.addView(titleTextView)
        layout.addView(editTextView)

        okButton.setText(android.R.string.ok)
        val okButtonLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        okButtonLayoutParams.weight = 0f
        okButton.layoutParams = okButtonLayoutParams
        okButton.isEnabled = false

        okButton.setOnClickListener { dismiss() }

        layout.addView(okButton)

        val cancelButton = Button(context)
        cancelButton.setText(android.R.string.cancel)
        val clearButtonLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clearButtonLayoutParams.weight = 0f
        cancelButton.layoutParams = clearButtonLayoutParams

        cancelButton.setOnClickListener {
            text = ""
            dismiss()
        }

        layout.addView(cancelButton)

        setContentView(layout)
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        TODO("Not yet implemented")
    }

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        TODO("Not yet implemented")
    }

    override fun afterTextChanged(p0: Editable?) {
        text = editTextView.text.toString().trim()
        okButton.isEnabled = !editTextView.text.isBlank()
    }
}
