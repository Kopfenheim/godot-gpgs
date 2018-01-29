def can_build(plat):
    return plat == 'android'

def configure(env):
    if env['platform'] == 'android':
        env.android_add_dependency("compile 'com.google.android.gms:play-services-games:11.8.0'")
        env.android_add_dependency("compile 'com.google.android.gms:play-services-auth:11.8.0'")
        env.android_add_java_dir("android")
        env.android_add_to_manifest("android/AndroidManifestChunk.xml")
        env.disable_module()
