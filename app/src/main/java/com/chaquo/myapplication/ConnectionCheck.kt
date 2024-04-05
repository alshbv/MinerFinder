package com.chaquo.myapplication

object ConnectionCheck {
    val myList: MutableList<String> = mutableListOf()

    fun addItem(item: String) {
        myList.add(item)
    }

    fun removeItem(item: String) {
        myList.remove(item)
    }

    fun clearList() {
        myList.clear()
    }
}
