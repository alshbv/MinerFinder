package com.chaquo.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.chaquo.myapplication.databinding.ActivityAccountBinding
import com.chaquo.myapplication.db.AppDatabase
import com.chaquo.myapplication.db.User
import kotlinx.coroutines.launch

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [LoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    var currentUser: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LoginFragment", "onCreate")
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("LoginFragment", "onCreateView")
        return inflater.inflate(R.layout.fragment_login, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var username = view.findViewById<EditText>(R.id.login_userNameEntry)
        var password = view.findViewById<EditText>(R.id.login_passwordEntry)

        val loginButton = view.findViewById<Button>(R.id.btn_login)

        loginButton.setOnClickListener {
            // Call the function you want to execute when the button is clicked
            login(username, password)
            //Log.d("UserPass", username.text.toString())
            //Log.d("UserPass", password.text.toString())

        }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment BlankFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            LoginFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }


private fun login(username: EditText, password: EditText) {
    var user = db().userDao().findByName(username.text.toString(), password.text.toString())

    if (user != null) {
        Log.d("LOGIN", "FOUND USER")
        db().userDao().log_in_out(user.uid, true)
        this.currentUser = user.username.toString()

        // cleanup
        username.text.clear()
        password.text.clear()

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)

    }
    else {
        Log.d("LOGIN", "NO USER")
    }

    // clear inputs ui
    username.text.clear()
    password.text.clear()
}




    private fun db(): AppDatabase {
        return Room.databaseBuilder(
            requireContext(),
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }



}