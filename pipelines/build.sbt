assemblyJarName in assembly := "bfx-examples-pipelines.jar" //-" + version.value + ".jar"
mainClass       in assembly := Some("dagr.core.cmdline.DagrCoreMain")

