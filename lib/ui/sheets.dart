import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/chat_state.dart';
import '../core/models.dart';
import 'design.dart';

Future<void> showProviders(BuildContext context, WidgetRef ref) async {
  final selected = await showModalBottomSheet<AiProvider>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => const _ProviderSheet(),
  );
  if (selected == null) return;
  ref.read(chatProvider.notifier).provider(selected);
  if (context.mounted) await showConnection(context, ref);
}

Future<void> showModels(BuildContext context, WidgetRef ref) async {
  final state = ref.read(chatProvider);
  if (state.key.isEmpty || (state.provider.custom && state.baseUrl.isEmpty)) {
    await showConnection(context, ref);
    return;
  }
  if (state.models.isEmpty) {
    final loaded = await ref.read(chatProvider.notifier).loadModels();
    if (!loaded || !context.mounted) return;
  }
  if (!context.mounted) return;
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => const _ModelSheet(),
  );
}

Future<void> showConnection(BuildContext context, WidgetRef ref) =>
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const _ConnectionSheet(),
    );

class _SheetShell extends StatelessWidget {
  const _SheetShell({
    required this.title,
    required this.subtitle,
    required this.child,
  });

  final String title;
  final String subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final availableHeight = media.size.height - media.viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
      child: Glass(
        radius: 28,
        blur: 24,
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: (availableHeight * .86).clamp(320.0, 720.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 38,
                    height: 4,
                    margin: const EdgeInsets.only(top: 10, bottom: 20),
                    decoration: BoxDecoration(
                      color: const Color(0xFF495361),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -.6,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
                  child: Text(
                    subtitle,
                    style: const TextStyle(color: muted, fontSize: 11),
                  ),
                ),
                Expanded(child: child),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ProviderSheet extends ConsumerWidget {
  const _ProviderSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final current = ref.watch(chatProvider).provider;
    return _SheetShell(
      title: 'Провайдеры',
      subtitle: 'Выберите API для подключения',
      child: ListView.separated(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(14, 0, 14, 24),
        itemCount: providers.length,
        separatorBuilder: (_, __) => const SizedBox(height: 7),
        itemBuilder: (context, index) {
          final provider = providers[index];
          final active = provider.id == current.id;
          return Material(
            color: active
                ? const Color(0xFF202B37)
                : const Color(0xFF131922),
            borderRadius: BorderRadius.circular(16),
            child: InkWell(
              onTap: () => Navigator.pop(context, provider),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                height: 62,
                padding: const EdgeInsets.symmetric(horizontal: 13),
                decoration: BoxDecoration(
                  border: Border.all(
                    color: active ? const Color(0xFF3A5667) : line,
                  ),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    ProviderBadge(provider),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            provider.name,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          Text(
                            provider.custom
                                ? 'OpenAI-compatible API'
                                : 'Официальное подключение',
                            style: const TextStyle(color: muted, fontSize: 9),
                          ),
                        ],
                      ),
                    ),
                    if (active)
                      const Icon(Icons.check_rounded, color: accent, size: 20),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ModelSheet extends ConsumerStatefulWidget {
  const _ModelSheet();

  @override
  ConsumerState<_ModelSheet> createState() => _ModelSheetState();
}

class _ModelSheetState extends ConsumerState<_ModelSheet> {
  String query = '';

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final models = state.models
        .where(
          (model) => '${model.name} ${model.id}'
              .toLowerCase()
              .contains(query.toLowerCase()),
        )
        .toList();
    return _SheetShell(
      title: 'Модели',
      subtitle: '${state.provider.name} · ${state.models.length} доступно',
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    onChanged: (value) => setState(() => query = value),
                    decoration: const InputDecoration(
                      prefixIcon: Icon(Icons.search_rounded, color: muted),
                      hintText: 'Найти модель',
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                RoundButton(
                  icon: Icons.refresh_rounded,
                  size: 52,
                  onTap: () => ref.read(chatProvider.notifier).loadModels(),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 24),
              itemCount: models.length,
              itemBuilder: (context, index) {
                final model = models[index];
                final active = model.id == state.model;
                return Material(
                  color: active
                      ? const Color(0xFF1D2B36)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                  child: InkWell(
                    onTap: () {
                      ref.read(chatProvider.notifier).model(model.id);
                      Navigator.pop(context);
                    },
                    borderRadius: BorderRadius.circular(14),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 11,
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  model.name,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  model.id,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    color: muted,
                                    fontSize: 9,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          if (active)
                            const Icon(
                              Icons.check_rounded,
                              color: accent,
                              size: 19,
                            ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _ConnectionSheet extends ConsumerStatefulWidget {
  const _ConnectionSheet();

  @override
  ConsumerState<_ConnectionSheet> createState() => _ConnectionSheetState();
}

class _ConnectionSheetState extends ConsumerState<_ConnectionSheet> {
  bool keyVisible = false;

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final controller = ref.read(chatProvider.notifier);
    return _SheetShell(
      title: 'Подключение',
      subtitle: 'Ключ остаётся только в памяти приложения',
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 28),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _Label('Провайдер'),
            const SizedBox(height: 7),
            Material(
              color: const Color(0xFF141A23),
              borderRadius: BorderRadius.circular(16),
              child: InkWell(
                onTap: () {
                  final rootContext = Navigator.of(context, rootNavigator: true).context;
                  Navigator.pop(context);
                  Future<void>.delayed(const Duration(milliseconds: 180), () {
                    if (rootContext.mounted) showProviders(rootContext, ref);
                  });
                },
                borderRadius: BorderRadius.circular(16),
                child: Container(
                  height: 54,
                  padding: const EdgeInsets.symmetric(horizontal: 13),
                  decoration: BoxDecoration(
                    border: Border.all(color: line),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    children: [
                      ProviderBadge(state.provider, size: 30),
                      const SizedBox(width: 11),
                      Expanded(child: Text(state.provider.name)),
                      const Icon(Icons.expand_more_rounded, color: muted),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 15),
            const _Label('API-ключ'),
            const SizedBox(height: 7),
            TextFormField(
              initialValue: state.key,
              onChanged: controller.apiKey,
              obscureText: !keyVisible,
              autocorrect: false,
              enableSuggestions: false,
              decoration: InputDecoration(
                hintText: state.provider.keyHint,
                suffixIcon: IconButton(
                  onPressed: () => setState(() => keyVisible = !keyVisible),
                  icon: Icon(
                    keyVisible
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    color: muted,
                  ),
                ),
              ),
            ),
            if (state.provider.custom) ...[
              const SizedBox(height: 15),
              const _Label('Base URL'),
              const SizedBox(height: 7),
              TextFormField(
                initialValue: state.baseUrl,
                onChanged: controller.baseUrl,
                autocorrect: false,
                decoration: const InputDecoration(
                  hintText: 'https://api.example.com/v1',
                ),
              ),
            ],
            const SizedBox(height: 18),
            SizedBox(
              width: double.infinity,
              height: 52,
              child: FilledButton.icon(
                onPressed: state.key.isEmpty || state.loadingModels
                    ? null
                    : () async {
                        final loaded = await controller.loadModels();
                        if (loaded && context.mounted) {
                          Navigator.pop(context);
                          await showModels(context, ref);
                        }
                      },
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF263B49),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                icon: state.loadingModels
                    ? const SizedBox.square(
                        dimension: 17,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.sync_rounded),
                label: Text(
                  state.models.isEmpty
                      ? 'Загрузить модели'
                      : 'Обновить модели (${state.models.length})',
                ),
              ),
            ),
            const SizedBox(height: 18),
            const _Label('Системная инструкция'),
            const SizedBox(height: 7),
            TextFormField(
              initialValue: state.system,
              onChanged: controller.system,
              minLines: 3,
              maxLines: 5,
              decoration: const InputDecoration(
                hintText: 'Как должен отвечать ассистент?',
              ),
            ),
            const SizedBox(height: 15),
            Row(
              children: [
                const _Label('Температура'),
                const Spacer(),
                Text(
                  state.temperature.toStringAsFixed(1),
                  style: const TextStyle(color: accent, fontSize: 11),
                ),
              ],
            ),
            Slider(
              value: state.temperature,
              max: 2,
              divisions: 20,
              onChanged: controller.temperature,
            ),
          ],
        ),
      ),
    );
  }
}

class _Label extends StatelessWidget {
  const _Label(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Text(
        text.toUpperCase(),
        style: const TextStyle(
          color: muted,
          fontSize: 9,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.1,
        ),
      );
}
