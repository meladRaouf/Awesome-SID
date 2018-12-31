package com.simprints.clientapi.activities.errors

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simprints.clientapi.R
import kotlinx.android.synthetic.main.activity_error.*

class ErrorActivity : AppCompatActivity(), ErrorContract.View {

    companion object {
        const val MESSAGE_KEY = "messageKey"
    }

    override lateinit var presenter: ErrorContract.Presenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error)

        presenter = ErrorPresenter(this, intent.getStringExtra(MESSAGE_KEY))
        textView_close_button.setOnClickListener { presenter.handleCloseClick() }
    }

    override fun closeActivity() {
        finishAffinity()
    }

    override fun setErrorMessageText(message: String) {
        textView_message.text = message
    }

}
