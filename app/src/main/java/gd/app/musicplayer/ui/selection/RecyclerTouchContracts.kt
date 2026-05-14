package gd.app.musicplayer.ui.selection

interface ItemTouchStateListener {
    fun onItemSelected()
    fun onItemCleared()
}

interface ItemMoveListener {
    fun onItemMove(fromPosition: Int, toPosition: Int)
}
