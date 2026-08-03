import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'ui/chat_screen.dart';
import 'ui/design.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(statusBarColor:Colors.transparent,navigationBarColor:Colors.transparent,statusBarIconBrightness:Brightness.light,navigationBarIconBrightness:Brightness.light));
  runApp(const ProviderScope(child:NitronBoxApp()));
}

class NitronBoxApp extends StatelessWidget {
  const NitronBoxApp({super.key});
  @override Widget build(BuildContext context)=>MaterialApp(title:'NitronBox',debugShowCheckedModeBanner:false,theme:nitronTheme(),home:const ChatScreen());
}
