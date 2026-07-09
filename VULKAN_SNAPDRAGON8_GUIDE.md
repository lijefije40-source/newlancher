# دليل دعم Vulkan لمعالجات Snapdragon 8

## نظرة عامة

تم تحسين Zalith Launcher خصيصاً لدعم معالجات Snapdragon 8 Series (Gen 1, Gen 2, Gen 3, Elite) مع كروت Adreno 7xx/8xx.

## المشكلة

معالجات Snapdragon 8 تواجه مشاكل مع renderers التقليدية:
- **LTW**: مشاكل Z-buffer في Minecraft 1.21.5+
- **GL4ES**: أداء ضعيف على Adreno 7xx/8xx
- **Zink العادي**: لا يستفيد من Mesa Turnip بشكل كامل

## الحل

### 1. Renderers المحسّنة

#### Vulkan Direct (موصى به لـ Snapdragon 8)
- **الاسم**: `Vulkan Direct (Snapdragon 8 Optimized)`
- **المميزات**:
  - دعم كامل لـ Vulkan 1.3
  - تحسينات خاصة بـ Adreno 7xx/8xx
  - Shader Cache متقدم (2GB)
  - أفضل أداء على Snapdragon 8

#### Vulkan Zink (محسّن)
- تم تحسينه بإعدادات Mesa Turnip
- يدعم Snapdragon 8 بشكل أفضل من الإصدار السابق
- Shader Cache 1GB

#### ANGLE (محسّن)
- يترجم OpenGL إلى Vulkan
- محسّن لـ Turnip
- خيار جيد إذا لم يعمل Zink

### 2. نظام تعريفات Turnip

#### ما هو Turnip؟
Turnip هو تعريف Mesa مفتوح المصدر لكروت Adreno يوفر دعم Vulkan محسّن.

#### التعريفات الموصى بها:

**Snapdragon 8 Elite (Adreno 8xx)**
```
turnip_a8xx_latest.zip
```

**Snapdragon 8 Gen 3 (Adreno 750)**
```
turnip_a750_v26.2.0-R7.zip
```

**Snapdragon 8 Gen 1/2 (Adreno 730/740)**
```
turnip_a7xx_v26.0.0-R7.zip
```

#### كيفية تثبيت تعريفات Turnip:

1. قم بتحميل ملف `.zip` المناسب لمعالجك
2. افتح Zalith Launcher > Settings > Turnip Driver Manager
3. اضغط على "Install Driver"
4. حدد ملف الـ ZIP
5. سيتم تثبيته تلقائياً واختياره كتعريف افتراضي

### 3. الإعدادات الموصى بها

#### لـ Snapdragon 8 Elite:
```
Renderer: Vulkan Direct (Snapdragon 8 Optimized)
Turnip Driver: turnip_a8xx_latest.zip
Memory: 4-8 GB
```

#### لـ Snapdragon 8 Gen 3:
```
Renderer: Vulkan Direct (Snapdragon 8 Optimized)
Turnip Driver: turnip_a750_v26.2.0-R7.zip
Memory: 3-6 GB
```

#### لـ Snapdragon 8 Gen 1/2:
```
Renderer: Vulkan Zink
Turnip Driver: turnip_a7xx_v26.0.0-R7.zip
Memory: 3-4 GB
```

## متغيرات البيئة المطبقة تلقائياً

عند استخدام Vulkan Direct على Snapdragon 8، يتم تطبيق:

```bash
# Core Vulkan
MESA_GL_VERSION_OVERRIDE=4.6
GALLIUM_DRIVER=zink
MESA_LOADER_DRIVER_OVERRIDE=zink

# Zink optimizations
ZINK_DESCRIPTORS=lazy
ZINK_DEBUG=compact,nir
mesa_glthread=true

# Turnip for Snapdragon 8
TU_DEBUG=noconform,syncdraw,nir,noubwc
MESA_VK_WSI_PRESENT_MODE=immediate
TU_OVERRIDE_HEAP_SIZE=8192

# Shader Cache
MESA_SHADER_CACHE_MAX_SIZE=2048M
MESA_DISK_CACHE_SINGLE_FILE=true

# libadrenotools injection
ADRENOTOOLS_DRIVER_FILE_REDIRECT=1
```

## استكشاف الأخطاء

### المشكلة: Renderer لا يعمل
**الحل**:
1. تأكد من تثبيت تعريف Turnip المناسب
2. جرب ANGLE بدلاً من Vulkan Direct
3. تحقق من Logs في `/sdcard/Android/data/.../log/`

### المشكلة: أداء ضعيف
**الحل**:
1. تأكد من استخدام Vulkan Direct
2. زد حجم Shader Cache
3. أغلق التطبيقات الأخرى

### المشكلة: تعطل اللعبة
**الحل**:
1. احذف Shader Cache
2. جرب تعريف Turnip أقدم
3. استخدم ANGLE كخيار بديل

## مصادر تعريفات Turnip

يمكن تحميل التعريفات من:
- [K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers/releases)
- [The412Banner/Banners-Turnip](https://github.com/The412Banner/Banners-Turnip/releases)
- [MrPurple666/purple-turnip](https://github.com/MrPurple666/purple-turnip/releases)

## ملاحظات مهمة

1. **Minecraft 1.21.5+**: استخدم Vulkan Direct أو ANGLE (LTW لديه مشاكل Z-buffer)
2. **Mods**: بعض mods التحسينية قد تتعارض (Sodium, Iris على GL4ES)
3. **التحديثات**: تحقق من وجود تحديثات لتعريفات Turnip بانتظام

## الدعم

إذا واجهت مشاكل:
1. افتح Issue على GitHub
2. أرفق Logs من `/sdcard/Android/data/.../log/`
3. اذكر موديل جهازك ونوع المعالج
4. اذكر إصدار Minecraft والـ renderer المستخدم

---

**Content was rephrased for compliance with licensing restrictions**
المحتوى مبني على بحث وتجميع من مصادر عامة متعددة.
