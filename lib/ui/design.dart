import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../core/models.dart';

const ink = Color(0xFF07090E);
const surface = Color(0xFF121720);
const line = Color(0xFF29313D);
const muted = Color(0xFF8D98A8);
const accent = Color(0xFF8BD9F7);

ThemeData nitronTheme() => ThemeData(
  brightness: Brightness.dark,
  scaffoldBackgroundColor: ink,
  colorScheme: const ColorScheme.dark(primary: accent, surface: surface, outline: line),
  textTheme: GoogleFonts.manropeTextTheme(ThemeData.dark().textTheme).apply(bodyColor: const Color(0xFFF2F4F7), displayColor: const Color(0xFFF2F4F7)),
  splashFactory: InkRipple.splashFactory,
  inputDecorationTheme: InputDecorationTheme(
    filled: true, fillColor: const Color(0xFF141A23), contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
    hintStyle: const TextStyle(color: Color(0xFF657080), fontSize: 13),
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: line)),
    enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: line)),
    focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: accent, width: 1.3)),
  ),
);

class Glass extends StatelessWidget {
  const Glass({super.key, required this.child, this.radius = 22, this.padding, this.blur = 18, this.onTap});
  final Widget child;
  final double radius;
  final EdgeInsets? padding;
  final double blur;
  final VoidCallback? onTap;

  @override Widget build(BuildContext context) {
    final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        boxShadow: const [BoxShadow(color: Color(0x52000000), blurRadius: 24, offset: Offset(0, 12))],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: Material(
            color: Colors.transparent,
            shape: shape,
            child: Ink(
              padding: padding,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(radius),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0x241F2A35), Color(0xD9121821), Color(0xE00D1219)],
                ),
                border: Border.all(color: const Color(0x2EFFFFFF)),
              ),
              child: InkWell(
                onTap: onTap,
                borderRadius: BorderRadius.circular(radius),
                splashColor: accent.withValues(alpha: .08),
                highlightColor: Colors.white.withValues(alpha: .025),
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: IgnorePointer(
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.all(Radius.circular(radius)),
                            gradient: const LinearGradient(
                              begin: Alignment.topLeft,
                              end: Alignment.center,
                              colors: [Color(0x12FFFFFF), Colors.transparent],
                            ),
                          ),
                        ),
                      ),
                    ),
                    child,
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class NitronLogo extends StatelessWidget {
  const NitronLogo({super.key, this.size = 48});
  final double size;
  @override Widget build(BuildContext context) => CustomPaint(size: Size.square(size), painter: _LogoPainter());
}

class _LogoPainter extends CustomPainter {
  @override void paint(Canvas canvas, Size size) {
    final a = Path()..moveTo(size.width*.12,size.height*.76)..lineTo(size.width*.43,size.height*.12)..quadraticBezierTo(size.width*.48,size.height*.04,size.width*.56,size.height*.19)..lineTo(size.width*.66,size.height*.37)..lineTo(size.width*.47,size.height*.77)..lineTo(size.width*.22,size.height*.88)..quadraticBezierTo(size.width*.07,size.height*.94,size.width*.12,size.height*.76)..close();
    final b = Path()..moveTo(size.width*.88,size.height*.24)..lineTo(size.width*.57,size.height*.88)..quadraticBezierTo(size.width*.52,size.height*.96,size.width*.44,size.height*.81)..lineTo(size.width*.34,size.height*.63)..lineTo(size.width*.53,size.height*.23)..lineTo(size.width*.78,size.height*.12)..quadraticBezierTo(size.width*.93,size.height*.06,size.width*.88,size.height*.24)..close();
    canvas.drawPath(a, Paint()..shader=const LinearGradient(colors:[Color(0xFFE3F8FF),Color(0xFF69D1F2),Color(0xFF3F7892)]).createShader(Offset.zero&size));
    canvas.drawPath(b, Paint()..shader=const LinearGradient(colors:[Colors.white,Color(0xFFA9DFF4),Color(0xFF46798F)]).createShader(Offset.zero&size));
  }
  @override bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class ProviderBadge extends StatelessWidget {
  const ProviderBadge(this.provider, {super.key, this.size = 34});
  final AiProvider provider; final double size;
  @override Widget build(BuildContext context) => Container(width:size,height:size,alignment:Alignment.center,decoration:BoxDecoration(color:Color(provider.color),borderRadius:BorderRadius.circular(size*.3)),child:Text(provider.short,style:TextStyle(fontSize:size*.25,fontWeight:FontWeight.w800,color:Colors.white)));
}

class RoundButton extends StatelessWidget {
  const RoundButton({super.key, required this.icon, required this.onTap, this.size=44});
  final IconData icon; final VoidCallback onTap; final double size;
  @override Widget build(BuildContext context) => SizedBox.square(dimension:size,child:Material(color:const Color(0xFF151B24),shape:RoundedRectangleBorder(borderRadius:BorderRadius.circular(13),side:const BorderSide(color:line)),clipBehavior:Clip.antiAlias,child:InkWell(onTap:onTap,child:Icon(icon,size:19,color:const Color(0xFFB3BCC9)))));
}
