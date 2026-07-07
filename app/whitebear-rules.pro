# 白い熊 fork rules.
#
# R8's optimizer (inlining + register allocation) miscompiles the huge Compose method
# EpubReaderScreenKt.EpubReaderHost$...: ART rejects the class with
# "VerifyError: type Long (Low Half) unexpected as arg to if-eqz/if-nez" and the app
# crashes on startup (seen with AGP 9.0.0 on build 1.0.51+30). Shrinking and
# obfuscation stay on; only the optimizer is disabled.
-dontoptimize
