# JGit resolves its signing SPIs through ServiceLoader; stripping these makes
# signing silently degrade to a no-op instead of failing loudly.
-keep class * implements org.eclipse.jgit.lib.Signer { *; }
-keep class * implements org.eclipse.jgit.lib.SignerFactory { *; }
-keep class * implements org.eclipse.jgit.lib.SignatureVerifierFactory { *; }
-keep class * implements org.eclipse.jgit.transport.SshSessionFactory { *; }
-keep class * implements org.eclipse.jgit.lib.SshSessionFactory { *; }
-keep class org.eclipse.jgit.internal.JGitText { <init>(); *; }

# JGit references JMX and java.lang.management for cache statistics, which the
# Android platform does not ship.
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn java.lang.ProcessHandle
-dontwarn org.ietf.jgss.**

-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
