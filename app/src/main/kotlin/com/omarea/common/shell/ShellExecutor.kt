package com.omarea.common.shell

import java.io.IOException

class ShellExecutor {
    companion object {
        private var extraEnvPath = ""
        private var defaultEnvPath = ""

        private var extraTmpDir = ""

        @JvmStatic
        fun setExtraEnvPath(extraEnvPath: String) {
            Companion.extraEnvPath = extraEnvPath
        }

        @JvmStatic
        fun setTmpDir(tmpDir: String?) {
            extraTmpDir = tmpDir ?: ""
        }

        private fun getEnvPath(): String? {
            if (extraEnvPath.isNotEmpty()) {
                if (defaultEnvPath.isEmpty()) {
                    try {
                        val process = Runtime.getRuntime().exec("sh")
                        val outputStream = process.outputStream
                        outputStream.write("echo \$PATH".toByteArray())
                        outputStream.flush()
                        outputStream.close()

                        val inputStream = process.inputStream
                        val cache = ByteArray(16384)
                        val length = inputStream.read(cache)
                        inputStream.close()
                        process.destroy()

                        val path = String(cache, 0, length).trim()
                        if (path.isNotEmpty()) {
                            defaultEnvPath = path
                        } else {
                            throw RuntimeException("Unable to obtain \$PATH parameter")
                        }
                    } catch (ex: Exception) {
                        defaultEnvPath = "/system/bin:/vendor/bin:/odm/bin:/system/xbin:/vendor/xbin:/system/sbin:/sbin"
                    }
                }

                val path = defaultEnvPath

                return "PATH=$path:$extraEnvPath"
            }

            return null
        }

        private fun buildEnvExportScript(): String? {
            val script = StringBuilder()

            val envPath = getEnvPath()
            if (envPath != null) {
                script.append("export ").append(envPath).append("\n")
            }

            if (extraTmpDir.isNotEmpty()) {
                script.append("export TMPDIR='").append(extraTmpDir).append("'\n")
            }

            return if (script.isNotEmpty()) script.toString() else null
        }

        @Throws(IOException::class)
        private fun getProcess(run: String): Process {
            val env = buildEnvExportScript()
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(run)
            if (env != null) {
                val outputStream = process.outputStream
                outputStream.write(env.toByteArray())
                outputStream.flush()
            }
            return process
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getSuperUserRuntime(): Process {
            return try {
                getProcess("su")
            } catch (e: IOException) {
                getProcess("sh")
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getRuntime(): Process {
            return getProcess("sh")
        }
    }
}
