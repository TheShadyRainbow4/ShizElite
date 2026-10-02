import sys
with open('manager/src/main/java/moe/shizuku/manager/utils/UpdateHelper.kt', 'r') as f:
    lines = f.readlines()

new_logic = '''        installIntent.setPackage("app.pwhs.universalinstaller")
        try {
            appContext.startActivity(installIntent)
            return
        } catch (e: Exception) {
            installIntent.setPackage(null)
            installIntent.setPackage("com.samsung.android.packageinstaller")
            try {
                appContext.startActivity(installIntent)
                return
            } catch (e2: Exception) {
                installIntent.setPackage(null)
                installIntent.setPackage("com.google.android.packageinstaller")
                try {
                    appContext.startActivity(installIntent)
                    return
                } catch (e3: Exception) {
                    installIntent.setPackage(null)
                    val chooser = Intent.createChooser(installIntent, "Choose installer")
                    chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appContext.startActivity(chooser)
                }
            }
        }\n'''

lines[174:198] = [new_logic]

with open('manager/src/main/java/moe/shizuku/manager/utils/UpdateHelper.kt', 'w') as f:
    f.writelines(lines)
