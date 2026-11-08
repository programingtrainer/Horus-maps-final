package com.horusmaps.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HorusLegalDocument { PRIVACY, TERMS }

private data class LegalSection(val heading: String, val body: String)

private fun legalSections(document: HorusLegalDocument): List<LegalSection> =
    if (document == HorusLegalDocument.PRIVACY) listOf(
        LegalSection(
            "1. المعلومات التي نجمعها",
            "عند إنشاء حساب، نعالج الاسم الأول واسم العائلة، واسم البريد الذي تختاره وينتهي بـ @horus.com، ورقم الهاتف إذا اخترت إضافته. نعالج كلمة المرور لإنشاء وسيلة تحقق آمنة؛ ولا نحفظها كنص واضح، بل يخزن الخادم بصمتها باستخدام PBKDF2-SHA256 مع قيمة ملح مستقلة. كما تُنشأ جلسة تسجيل دخول ويُحفظ مُعرّفها المشفّر وموعد انتهائها."
        ),
        LegalSection(
            "2. الموقع الجغرافي",
            "إذا منحت التطبيق إذن الموقع، يستخدم HorusMaps موقع جهازك لعرض موقعك وتوجيه مركز الخريطة إليه. وفق وظائف الإصدار الحالي، لا يُضاف إحداثي GPS إلى بيانات ملف حسابك ولا يُرسل إلى واجهة تسجيل الحساب. ومع ذلك، فإن تحميل مربعات الخريطة للمنطقة المعروضة قد يكشف لمزوّد الخرائط عنوان الاتصال وطلبات المربعات والمنطقة التقريبية التي يجري عرضها."
        ),
        LegalSection(
            "3. صورة الملف الشخصي",
            "إذا اخترت صورة للملف الشخصي، تُحفظ نسخة على جهازك، وقد تُرفع نسخة إلى خدمة التخزين المرتبطة بالبنية السحابية للتطبيق حتى تظهر عند تسجيل الدخول من جديد. لا نطلب الوصول إلى صورك لمجرد إنشاء الحساب؛ اختيار الصورة يتم بقرار منك."
        ),
        LegalSection(
            "4. كيف نستخدم المعلومات",
            "نستخدم البيانات لإنشاء الحساب والتحقق من تسجيل الدخول، وإدارة جلسة الاستخدام، وعرض بيانات الملف الشخصي، وحفظ صورة الملف الشخصي التي تختارها، وتقديم إعدادات التطبيق والخريطة، وحماية الخدمة من الاستخدام غير المصرح به ومعالجة الأعطال."
        ),
        LegalSection(
            "5. التخزين ومقدمو الخدمات",
            "تُعالج بيانات الحساب والجلسات عبر خدمات Cloudflare Workers وD1، وقد تُخزّن صور الملف الشخصي عبر خدمة التخزين السحابي المرتبطة بالتطبيق. وتعتمد الخريطة على OpenFreeMap وبيانات OpenMapTiles وOpenStreetMap؛ وعند تحميل الخريطة يتصل التطبيق بخدمات الخرائط مباشرة. تخضع معالجة تلك الخدمات لسياساتها وشروطها الخاصة."
        ),
        LegalSection(
            "6. المشاركة والإعلانات",
            "لا نبيع بيانات الحساب الشخصية. ولا نستخدم بيانات الحساب لإنشاء إعلانات موجهة في وظائف الإصدار الحالي. قد تُعالج البيانات لدى مزودي البنية التقنية والخرائط بالقدر اللازم لتشغيل الخدمة أو حمايتها أو تنفيذ التزامات قانونية."
        ),
        LegalSection(
            "7. مدة الاحتفاظ والحذف",
            "نحتفظ ببيانات الحساب ما دام الحساب قائمًا، وتستمر جلسة الدخول لمدة تصل إلى 30 يومًا ما لم تُنهَ قبل ذلك بتسجيل الخروج أو حذف الحساب. يمكنك طلب حذف حسابك من إعدادات الحساب داخل التطبيق بعد تأكيد كلمة المرور. يؤدي الحذف إلى إزالة سجل الحساب والجلسات المرتبطة به، كما يحاول التطبيق حذف صورة الملف الشخصي المخزنة على الخادم والجهاز."
        ),
        LegalSection(
            "8. حماية المعلومات",
            "نستخدم اتصال HTTPS بين التطبيق وخدمات HorusMaps، ولا نخزن كلمة المرور الأصلية كنص واضح، كما تُحفظ بصمات رموز الجلسات بدلًا من الرموز الأصلية في قاعدة البيانات. نتخذ تدابير تقنية معقولة، لكن لا يمكن ضمان أمان مطلق لأي نظام متصل بالإنترنت."
        ),
        LegalSection(
            "9. خياراتك وأذونات الجهاز",
            "يمكنك اختيار عدم إضافة رقم هاتف أو صورة شخصية، كما يمكنك رفض إذن الموقع أو سحبه من إعدادات جهازك؛ وقد يؤدي ذلك إلى تعطيل عرض موقعك الحالي فقط. يمكنك مراجعة بيانات ملفك وتسجيل الخروج أو حذف الحساب من داخل التطبيق."
        ),
        LegalSection(
            "10. التعديلات والتواصل",
            "قد نحدّث هذه السياسة عند إضافة وظائف أو تغيير طريقة معالجة البيانات. سنعرض تاريخ آخر تحديث في هذه الصفحة. إذا كانت لديك أسئلة أو طلب متعلق ببياناتك، فاستخدم قنوات التواصل الرسمية التي يعلنها HorusMaps."
        )
    ) else listOf(
        LegalSection(
            "1. الموافقة على الشروط",
            "باستخدام HorusMaps أو إنشاء حساب، فإنك تقر بأنك قرأت هذه الشروط وفهمتها وتوافق على الالتزام بها. إذا لم توافق عليها، فلا تنشئ حسابًا أو تستخدم الخدمات التي تتطلب الموافقة."
        ),
        LegalSection(
            "2. الحساب ومسؤوليته",
            "قدّم بيانات صحيحة ومحدّثة عند إنشاء الحساب، وحافظ على سرية كلمة المرور ورموز الدخول، ولا تسمح للآخرين باستخدام حسابك دون إذنك. أنت مسؤول عن الأنشطة التي تتم من خلال حسابك، وعليك إبلاغنا عبر قنواتنا الرسمية إذا اشتبهت في استخدام غير مصرح به."
        ),
        LegalSection(
            "3. الاستخدام المقبول",
            "تتعهد باستخدام التطبيق لأغراض مشروعة، وألا تستخدمه لانتهاك حقوق الآخرين أو القوانين، أو لمحاولة تعطيل الخدمة أو الوصول غير المصرح به إلى أنظمتها، أو إرسال محتوى ضار، أو إجراء هندسة عكسية أو استخراج آلي للبيانات بما يخالف القوانين أو حقوق مزودي البيانات."
        ),
        LegalSection(
            "4. الخرائط والموقع",
            "قد يعرض التطبيق خرائط وبيانات جغرافية مقدمة من أطراف خارجية. قد تكون بعض البيانات غير مكتملة أو متأخرة أو غير دقيقة، وقد تتغير الطرق والأماكن والظروف على أرض الواقع. تحقّق دائمًا من العلامات والإشارات والظروف الفعلية، والتزم بقواعد المرور. لا تعتمد على التطبيق وحده في حالات الطوارئ أو القرارات التي تتطلب دقة ملاحية مضمونة."
        ),
        LegalSection(
            "5. إذن الموقع",
            "استخدام ميزة عرض موقعك الحالي اختياري ويتطلب إذنًا من نظام التشغيل. يمكنك رفض الإذن أو سحبه في أي وقت، وعندها لن يتمكن التطبيق من عرض موقعك الحالي. تظل مسؤولًا عن اختيار المسار الآمن والتحقق من ملاءمته."
        ),
        LegalSection(
            "6. خدمات وحقوق الأطراف الأخرى",
            "تستخدم الخريطة بيانات وخدمات أطراف أخرى، ومنها OpenMapTiles وOpenStreetMap، وتظل تلك البيانات خاضعة للتراخيص وشروط الاستخدام الخاصة بها. لا تعني علامة HorusMaps امتلاك التطبيق لحقوق بيانات الخرائط أو محتوى الأطراف الأخرى. يجب احترام نسب البيانات إلى مصادرها."
        ),
        LegalSection(
            "7. التوفر والتحديثات",
            "نسعى إلى إبقاء التطبيق متاحًا وتحسينه، لكن قد تتوقف بعض الوظائف مؤقتًا بسبب الصيانة أو الاتصال أو أعطال مزودي الخدمات. يجوز تحديث التطبيق أو تعديل وظائفه أو إيقاف جزء منها لتحسين الخدمة أو لأسباب تشغيلية أو قانونية."
        ),
        LegalSection(
            "8. إيقاف الحساب وحذفه",
            "يمكنك حذف حسابك من إعدادات الحساب داخل التطبيق بعد تأكيد كلمة المرور. ويجوز تقييد الوصول أو إيقافه عند إساءة استخدام الخدمة أو وجود متطلبات أمنية أو قانونية، مع مراعاة القوانين السارية."
        ),
        LegalSection(
            "9. حدود المسؤولية",
            "يُقدّم التطبيق والبيانات المرتبطة به وفق حالة التوفر الحالية، دون ضمان أن تكون الخدمة متاحة بلا انقطاع أو أن تكون جميع معلومات الخرائط دقيقة أو مناسبة لغرض محدد. إلى الحد الذي يسمح به القانون، لا يتحمل HorusMaps مسؤولية الأضرار الناتجة عن الاعتماد الحصري على معلومات الخريطة أو انقطاع خدمات الأطراف الأخرى أو سوء استخدام التطبيق."
        ),
        LegalSection(
            "10. التعديلات والقانون المطبق",
            "قد نحدّث هذه الشروط عند تطوير التطبيق. استمرارك في استخدام الخدمة بعد نشر التعديلات يعني قبولك للشروط المحدّثة، ما لم يتطلب القانون موافقة جديدة. تُفسّر هذه الشروط وتُطبّق وفق القوانين واجبة التطبيق، دون الانتقاص من أي حقوق إلزامية يقررها القانون للمستخدم."
        )
    )

@Composable
fun HorusLegalDocumentPage(
    document: HorusLegalDocument,
    onBack: () -> Unit
) {
    val title = if (document == HorusLegalDocument.PRIVACY) "سياسة الخصوصية" else "شروط الاستخدام"
    val intro = if (document == HorusLegalDocument.PRIVACY) {
        "نحترم خصوصيتك ونسعى إلى توضيح البيانات التي يستخدمها HorusMaps وكيفية التعامل معها."
    } else {
        "توضح هذه الشروط القواعد الأساسية لاستخدام تطبيق HorusMaps وخدماته."
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FC)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF092744)).padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("HORUSMAPS  •  آخر تحديث: 8 نوفمبر 2026", color = Color(0xFFB9CDE0), fontSize = 10.sp)
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F1FC))
                ) {
                    Text(intro, modifier = Modifier.padding(18.dp), color = Color(0xFF23415F), fontSize = 13.sp, lineHeight = 22.sp)
                }
                legalSections(document).forEach { section ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(section.heading, color = Color(0xFF102D4A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(section.body, color = Color(0xFF4B5D70), fontSize = 12.sp, lineHeight = 21.sp, textAlign = TextAlign.Start)
                        }
                    }
                }
                Text(
                    "هذه صياغة تشغيلية أولية مبنية على وظائف الإصدار الحالي. يُنصح بمراجعتها قانونيًا قبل النشر التجاري أو التوسع في جمع البيانات.",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    color = Color(0xFF78899B), fontSize = 10.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
