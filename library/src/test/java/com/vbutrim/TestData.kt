package com.vbutrim

import com.vbutrim.file.AbsolutePath
import com.vbutrim.file.child
import kotlin.io.path.Path

val resourcesDirectoryPath = Path("src/test/resources")
val resourcesDirectory = resourcesDirectoryPath.toFile()

val textsDirectoryPath = resourcesDirectoryPath.child("texts")
val fightClubPath = textsDirectoryPath.child("C. Palahniuk 'Fight club'")
val fightClubAbsolutePath = AbsolutePath.cons(fightClubPath)
val starWarsPath = textsDirectoryPath.child("Star Wars Mon Mothma")
val starWarsAbsolutePath = AbsolutePath.cons(starWarsPath)
