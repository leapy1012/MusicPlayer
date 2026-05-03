package gd.app.lib.model.lrc.renderer

import gd.app.lib.model.lrc.resource.LyricText

interface OnLyricChangeListener {
    fun onLyricChanged(data: LyricText?)
}