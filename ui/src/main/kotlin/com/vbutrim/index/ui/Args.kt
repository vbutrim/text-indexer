package com.vbutrim.index.ui

internal data class Args(private val args: Map<Arg.Type, String>) {
    companion object {
        fun parse(args: Array<String>): Args {
            return Args(
                args.map { Arg.parseOrThrow(it) }
                    .associateBy({ it.type }, { it.stringValue })
            )
        }
    }

    fun getLongValue(type: Arg.Type): Long? {
        return args[type]?.toLong()
    }

    fun getBooleanValue(type: Arg.Type): Boolean? {
        return args[type]?.toBoolean()
    }

    data class Arg(val type: Type, val stringValue: String) {

        companion object {
            fun parseOrThrow(arg: String): Arg {
                require(arg.count { it == '=' } == 1) {
                    "not valid argument $arg"
                }

                val nameAndValue = arg.split("=")

                val type = Type.BY_VALUE[nameAndValue[0]]
                require(type != null) {
                    "unknown arg " + nameAndValue[0]
                }

                return Arg(type, nameAndValue[1])
            }
        }

        enum class Type(val value: String) {
            SYNC_DELAY_TIME_IN_SECONDS("syncDelayTimeInSeconds"),
            DEBUG_PANEL_IS_ENABLED("debugPanelIsEnabled"),
            SHOW_SYNC_STATUS("showSyncStatus")
            ;

            companion object {
                internal val BY_VALUE = Type.values().associateBy { it.value }
            }
        }
    }
}