package danb.speedrunbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import danb.speedrunbrowser.utils.Util

class NotFoundFragment : Fragment() {

    lateinit var data: Uri

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        data = args?.getParcelable(ARG_URL) ?: Uri.EMPTY
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_not_found, container, false)

        v.findViewById<Button>(R.id.buttonTryWebsite).setOnClickListener {
            openWebsite()
        }

        v.findViewById<Button>(R.id.buttonMainPage).setOnClickListener {
            showMainPage()
        }

        return v
    }

    private fun openWebsite() {
        startActivity(Util.openInBrowser(requireContext(), data))
    }

    private fun showMainPage() {

        findNavController().navigate(R.id.gameListFragment, null, NavOptions.Builder().setLaunchSingleTop(true).build())

        val intent = Intent(requireContext(), SpeedrunBrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    companion object {
        const val ARG_URL = "url"
    }
}