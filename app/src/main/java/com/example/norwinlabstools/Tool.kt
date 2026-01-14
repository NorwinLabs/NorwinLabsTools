package com.example.norwinlabstools

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class Tool(
    val id: Int,
    val name: String,
    @DrawableRes val iconRes: Int,
    val version: String = "1.0.0",
    @ColorInt val color: Int = 0xFF6200EE.toInt(),
    val imageUrl: String? = null
)