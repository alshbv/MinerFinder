package com.chaquo.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager.widget.ViewPager
import com.chaquo.myapplication.databinding.ActivityLoginBinding
import com.google.android.material.tabs.TabLayout
import androidx.room.Room
import com.chaquo.myapplication.databinding.ActivityAccountBinding
import com.chaquo.myapplication.db.AppDatabase
import com.chaquo.myapplication.db.User



class Login: AppCompatActivity() {
    private lateinit var viewBinding: ActivityLoginBinding
    var currentUser : String = ""


    // various variables used for tabs
    private lateinit var Pager: ViewPager
    private lateinit var Tab: TabLayout
    private lateinit var Bar: Toolbar


    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        /*
        supportActionBar?.title = "MinerFinder Login"

        val loginButton = findViewById<Button>(R.id.btn_login)
        val switchButton1 = findViewById<Button>(R.id.btn_switch_1)

        loginButton.setOnClickListener {
            // Call the function you want to execute when the button is clicked
            login()
            //Log.d("UserPass", username.text.toString())
            //Log.d("UserPass", password.text.toString())
        }

        switchButton1.setOnClickListener {
            signUpView(it)
        }
        */







        Pager = findViewById(R.id.viewPager)
        Tab = findViewById(R.id.tabs)
        Bar = findViewById(R.id.toolbar)

        setSupportActionBar(Bar)
        supportActionBar?.title = "MinerFinder Login"

        val adapter = ViewPagerAdapter123(supportFragmentManager)

        adapter.addFragment(LoginFragment(), "Login")
        adapter.addFragment(SignUpFragment(), "Sign Up")

        Pager.adapter = adapter

        // bind the viewPager with the TabLayout.
        Tab.setupWithViewPager(Pager)

        Log.d("TabsDebug", "Number of tabs: ${adapter.count}")
        Log.d("TabsDebug", (Pager.adapter as ViewPagerAdapter123).getPageTitle(0).toString())



    }

    /*
    private fun login() {
        val username = findViewById<EditText>(R.id.usernameInput)
        val password = findViewById<EditText>(R.id.passwordInput)
        var user = db().userDao().findByName(username.text.toString(), password.text.toString())

        if (user != null) {
            Log.d("LOGIN", "FOUND USER")
            db().userDao().log_in_out(user.uid, true)
            this.currentUser = user.username.toString()
        }
        else {
            Log.d("LOGIN", "NO USER")
        }

        // clear inputs ui
        username.text.clear()
        password.text.clear()
    }
     */

    private fun db(): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }


}