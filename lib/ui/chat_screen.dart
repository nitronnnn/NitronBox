import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/chat_state.dart';
import '../core/models.dart';
import 'design.dart';
import 'sheets.dart';

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key});
  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final scaffoldKey = GlobalKey<ScaffoldState>();
  final composer = TextEditingController();
  final scroll = ScrollController();
  String lastMessageSignature = '';

  @override
  void dispose() {
    composer.dispose();
    scroll.dispose();
    super.dispose();
  }

  void send() {
    final value = composer.text;
    if (value.trim().isEmpty) return;
    ref.read(chatProvider.notifier).send(value);
    composer.clear();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final messages = state.active?.messages ?? const <ChatMessage>[];
    ref.listen(chatProvider.select((value) => value.error), (_, error) {
      if (error.isEmpty) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(error),
            behavior: SnackBarBehavior.floating,
            backgroundColor: const Color(0xFF292026),
            action: SnackBarAction(
              label: 'OK',
              onPressed: ref.read(chatProvider.notifier).clearError,
            ),
          ),
        );
    });
    final signature = messages.isEmpty
        ? ''
        : '${messages.last.id}:${messages.last.content.length}';
    if (signature != lastMessageSignature) {
      lastMessageSignature = signature;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !scroll.hasClients) return;
        final distance = scroll.position.maxScrollExtent - scroll.position.pixels;
        if (distance < 180 || state.streaming) {
        scroll.animateTo(
          scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeOutCubic,
        );
        }
      });
    }

    return Scaffold(
      key: scaffoldKey,
      resizeToAvoidBottomInset: true,
      drawer: _HistoryDrawer(close: () => scaffoldKey.currentState?.closeDrawer()),
      body: Stack(
        children: [
          const _Background(),
          SafeArea(
            child: Column(
              children: [
                _Header(
                  state: state,
                  menu: () => scaffoldKey.currentState?.openDrawer(),
                  model: () => showModels(context, ref),
                  settings: () => showConnection(context, ref),
                  newChat: ref.read(chatProvider.notifier).newChat,
                ),
                Expanded(
                  child: messages.isEmpty
                      ? _Welcome(
                          state: state,
                          connect: () => showModels(context, ref),
                          prompt: (value) => ref.read(chatProvider.notifier).send(value),
                        )
                      : _Messages(messages: messages, controller: scroll, streaming: state.streaming),
                ),
                _Composer(
                  controller: composer,
                  streaming: state.streaming,
                  send: send,
                  stop: ref.read(chatProvider.notifier).stop,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Background extends StatelessWidget {
  const _Background();
  @override
  Widget build(BuildContext context) => const DecoratedBox(
        decoration: BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(-.7, -1.1),
            radius: 1.5,
            colors: [Color(0xFF17303E), ink, Color(0xFF05070A)],
            stops: [0, .44, 1],
          ),
        ),
        child: SizedBox.expand(),
      );
}

class _Header extends StatelessWidget {
  const _Header({required this.state, required this.menu, required this.model, required this.settings, required this.newChat});
  final ChatState state;
  final VoidCallback menu, model, settings, newChat;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(10, 8, 10, 0),
        child: Glass(
          padding: const EdgeInsets.all(7),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 350;
              return Row(children: [
              RoundButton(icon: Icons.menu_rounded, onTap: menu),
              const SizedBox(width: 7),
              Expanded(
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: model,
                    borderRadius: BorderRadius.circular(14),
                    child: SizedBox(
                      height: 44,
                      child: Row(
                        children: [
                          ProviderBadge(state.provider, size: 28),
                          SizedBox(width: compact ? 7 : 10),
                          Expanded(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                if (!compact) Text(state.provider.name, style: const TextStyle(color: muted, fontSize: 9)),
                                Text(state.model.isEmpty ? 'Выберите модель' : state.model, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
                              ],
                            ),
                          ),
                          const Icon(Icons.expand_more_rounded, color: muted, size: 18),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              RoundButton(icon: Icons.tune_rounded, onTap: settings),
              if (!compact) ...[
                const SizedBox(width: 7),
                RoundButton(icon: Icons.add_rounded, onTap: newChat),
              ],
            ]);
            },
          ),
        ),
      );
}

class _Welcome extends StatelessWidget {
  const _Welcome({required this.state, required this.connect, required this.prompt});
  final ChatState state;
  final VoidCallback connect;
  final ValueChanged<String> prompt;
  @override
  Widget build(BuildContext context) => LayoutBuilder(
        builder: (context, box) => SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 22),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: box.maxHeight),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                NitronLogo(size: box.maxHeight < 480 ? 60 : 76),
                SizedBox(height: box.maxHeight < 480 ? 12 : 18),
                Text('NitronBox', style: TextStyle(fontSize: box.maxHeight < 480 ? 28 : 32, fontWeight: FontWeight.w800, letterSpacing: -1.2)),
                const SizedBox(height: 4),
                const Text('Любая модель. Один аккуратный чат.', style: TextStyle(color: muted, fontSize: 12)),
                SizedBox(height: box.maxHeight < 480 ? 18 : 26),
                Glass(
                  onTap: connect,
                  padding: const EdgeInsets.all(15),
                  radius: 19,
                  child: Row(
                    children: [
                      ProviderBadge(state.provider, size: 40),
                      const SizedBox(width: 13),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(state.key.isEmpty ? 'Не подключено' : 'Готово к работе', style: const TextStyle(color: muted, fontSize: 10)),
                            const SizedBox(height: 2),
                            Text(state.model.isEmpty ? 'Подключить ${state.provider.name}' : state.model, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                      const Icon(Icons.chevron_right_rounded, color: muted),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(child: _Prompt(icon: Icons.lightbulb_outline_rounded, text: 'Объяснить тему', onTap: () => prompt('Объясни сложную тему простыми словами'))),
                    const SizedBox(width: 10),
                    Expanded(child: _Prompt(icon: Icons.code_rounded, text: 'Помочь с кодом', onTap: () => prompt('Помоги разобраться с кодом'))),
                  ],
                ),
              ],
            ),
          ),
        ),
      );
}

class _Prompt extends StatelessWidget {
  const _Prompt({required this.icon, required this.text, required this.onTap});
  final IconData icon; final String text; final VoidCallback onTap;
  @override Widget build(BuildContext context) => Glass(onTap:onTap,radius:17,padding:const EdgeInsets.symmetric(horizontal:13,vertical:15),child:Row(children:[Icon(icon,color:accent,size:18),const SizedBox(width:8),Flexible(child:Text(text,maxLines:1,style:const TextStyle(fontSize:10,fontWeight:FontWeight.w600)))]));
}

class _Messages extends StatelessWidget {
  const _Messages({required this.messages,required this.controller,required this.streaming});
  final List<ChatMessage> messages;final ScrollController controller;final bool streaming;
  @override Widget build(BuildContext context)=>ListView.separated(controller:controller,padding:const EdgeInsets.fromLTRB(14,24,14,20),itemCount:messages.length,separatorBuilder:(_,__)=>const SizedBox(height:22),itemBuilder:(context,index){final message=messages[index];if(message.role=='user')return Align(alignment:Alignment.centerRight,child:Container(constraints:const BoxConstraints(maxWidth:330),padding:const EdgeInsets.symmetric(horizontal:15,vertical:11),decoration:BoxDecoration(color:const Color(0xFF20313D),borderRadius:const BorderRadius.only(topLeft:Radius.circular(18),topRight:Radius.circular(18),bottomLeft:Radius.circular(18),bottomRight:Radius.circular(5)),border:Border.all(color:const Color(0xFF354B59))),child:Text(message.content,style:const TextStyle(fontSize:13,height:1.5))));return Row(crossAxisAlignment:CrossAxisAlignment.start,children:[const Padding(padding:EdgeInsets.only(top:2),child:NitronLogo(size:28)),const SizedBox(width:10),Expanded(child:message.content.isEmpty&&streaming?const _Typing():MarkdownBody(data:message.content,selectable:true,styleSheet:MarkdownStyleSheet(p:const TextStyle(fontSize:13,height:1.55,color:Color(0xFFD0D7E0)),code:const TextStyle(fontFamily:'monospace',fontSize:12,color:Color(0xFFB8E7F8),backgroundColor:Color(0xFF11161D)),codeblockDecoration:BoxDecoration(color:const Color(0xFF0D1117),borderRadius:BorderRadius.circular(14),border:Border.all(color:line)),blockquoteDecoration:BoxDecoration(color:const Color(0xFF121A23),border:Border(left:BorderSide(color:accent,width:2))),h1:const TextStyle(fontSize:22,fontWeight:FontWeight.w800),h2:const TextStyle(fontSize:18,fontWeight:FontWeight.w700))))]);});
}

class _Typing extends StatelessWidget {const _Typing();@override Widget build(BuildContext context)=>const Padding(padding:EdgeInsets.only(top:8),child:SizedBox(width:26,height:12,child:LinearProgressIndicator(backgroundColor:Colors.transparent,color:accent,borderRadius:BorderRadius.all(Radius.circular(5)))));}

class _Composer extends StatelessWidget {
  const _Composer({required this.controller,required this.streaming,required this.send,required this.stop});
  final TextEditingController controller;final bool streaming;final VoidCallback send,stop;
  @override Widget build(BuildContext context)=>Padding(padding:const EdgeInsets.fromLTRB(10,4,10,8),child:Glass(radius:19,padding:const EdgeInsets.all(7),blur:14,child:Row(crossAxisAlignment:CrossAxisAlignment.end,children:[Expanded(child:TextField(controller:controller,minLines:1,maxLines:4,textInputAction:TextInputAction.newline,scrollPadding:const EdgeInsets.only(bottom:100),decoration:const InputDecoration(hintText:'Сообщение',filled:false,border:InputBorder.none,enabledBorder:InputBorder.none,focusedBorder:InputBorder.none,contentPadding:EdgeInsets.symmetric(horizontal:11,vertical:10)))),const SizedBox(width:6),ValueListenableBuilder<TextEditingValue>(valueListenable:controller,builder:(_,value,__)=>SizedBox.square(dimension:42,child:FilledButton(onPressed:streaming?stop:(value.text.trim().isEmpty?null:send),style:FilledButton.styleFrom(padding:EdgeInsets.zero,backgroundColor:accent,foregroundColor:ink,disabledBackgroundColor:const Color(0xFF252D38),shape:RoundedRectangleBorder(borderRadius:BorderRadius.circular(13))),child:Icon(streaming?Icons.stop_rounded:Icons.arrow_upward_rounded,size:19))))])));
}

class _HistoryDrawer extends ConsumerWidget {
  const _HistoryDrawer({required this.close});
  final VoidCallback close;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(chatProvider);
    final controller = ref.read(chatProvider.notifier);
    return Drawer(
      width: 310,
      backgroundColor: const Color(0xFF0E131B),
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const NitronLogo(size: 38),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text(
                      'NitronBox',
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
                    ),
                  ),
                  RoundButton(icon: Icons.close_rounded, onTap: close),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: SizedBox(
                width: double.infinity,
                height: 48,
                child: FilledButton.icon(
                  onPressed: () {
                    controller.newChat();
                    close();
                  },
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF20303C),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                  ),
                  icon: const Icon(Icons.add_rounded),
                  label: const Text('Новый чат'),
                ),
              ),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(18, 22, 18, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('ИСТОРИЯ', style: TextStyle(color: muted, fontSize: 9, fontWeight: FontWeight.w700, letterSpacing: 1)),
              ),
            ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                itemCount: state.chats.length,
                itemBuilder: (context, index) {
                  final chat = state.chats[index];
                  final active = chat.id == state.activeId;
                  return Material(
                    color: active ? const Color(0xFF192630) : Colors.transparent,
                    borderRadius: BorderRadius.circular(13),
                    child: InkWell(
                      onTap: () {
                        controller.openChat(chat.id);
                        close();
                      },
                      borderRadius: BorderRadius.circular(13),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 11),
                        child: Row(
                          children: [
                            const Icon(Icons.chat_bubble_outline_rounded, size: 17, color: muted),
                            const SizedBox(width: 9),
                            Expanded(child: Text(chat.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11))),
                            IconButton(
                              onPressed: () => controller.deleteChat(chat.id),
                              visualDensity: VisualDensity.compact,
                              icon: const Icon(Icons.delete_outline_rounded, size: 17, color: muted),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            const Padding(
              padding: EdgeInsets.all(18),
              child: Text('API-ключи не сохраняются', style: TextStyle(color: muted, fontSize: 9)),
            ),
          ],
        ),
      ),
    );
  }
}
