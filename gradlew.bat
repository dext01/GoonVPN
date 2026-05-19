@rem Gradle startup script for Windows
@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set DIRNAME=%~dp0
"%JAVA_HOME%\bin\java.exe" -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
