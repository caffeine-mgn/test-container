package pw.binom.testContainer

import pw.binom.doFreeze
import pw.binom.nextUuid
import kotlin.native.concurrent.SharedImmutable
import kotlin.random.Random

@SharedImmutable
val RUN_INSTANCE = Random.nextUuid().doFreeze()