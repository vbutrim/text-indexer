package com.vbutrim

import com.vbutrim.file.asAbsolutePath
import com.vbutrim.file.child
import kotlin.io.path.Path

private val resourcesDirectoryPath = Path("src/test/resources")

val textsDirectoryPath = resourcesDirectoryPath.child("texts")
val textsDirectoryAbsolutePath = textsDirectoryPath.asAbsolutePath()

val fightClubPath = textsDirectoryPath.child("C. Palahniuk 'Fight club'")
val fightClubAbsolutePath = fightClubPath.asAbsolutePath()

val starWarsPath = textsDirectoryPath.child("Star Wars Mon Mothma")
val starWarsAbsolutePath = starWarsPath.asAbsolutePath()

val textsMarkMansonDirectoryPath = textsDirectoryPath.child("Mark Manson")
val textsMarkMansonDirectoryAbsolutePath = textsMarkMansonDirectoryPath.asAbsolutePath()

val practicalGuideToHappinessPath = textsMarkMansonDirectoryPath.child("A Practical Guide to Happiness")
val practicalGuideToHappinessAbsolutePath = practicalGuideToHappinessPath.asAbsolutePath()

const val BE_CURIOUS_NOT_JUDGEMENTAL = "Be curious, not judgemental"

val BE_CURIOUS_NOT_JUDGEMENTAL_TOKENS = listOf("judgemental", "curious")