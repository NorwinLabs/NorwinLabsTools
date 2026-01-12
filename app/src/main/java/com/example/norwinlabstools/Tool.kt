package com.example.norwinlabstools

import androidx.annotation.DrawableRes

data class Tool(
    val id: Int,
    val name: String,
    @DrawableRes val iconRes: Int
)