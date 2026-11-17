package com.pranshulgg.recordmaster.utils

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object NavTransitions {

    fun enter(motionScheme: MotionScheme): EnterTransition =
        slideInHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())

    fun exit(motionScheme: MotionScheme): ExitTransition =
        slideOutHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            targetOffsetX = { -it }
        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec())

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun popEnter(motionScheme: MotionScheme): EnterTransition =
        slideInHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            initialOffsetX = { -it }
        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun popExit(motionScheme: MotionScheme): ExitTransition =
        slideOutHorizontally(
            animationSpec = motionScheme.defaultSpatialSpec(),
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
}

