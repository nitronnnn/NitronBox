import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/chat_state.dart';
import '../core/models.dart';
import 'design.dart';

Future<void> showConnection(
  BuildContext context,
  WidgetRef ref, {
  bool openModels = false,
}) =>
    showModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black.withValues(alpha: .72),
      builder: (_) => _ConnectionSheet(openModels: openModels),
    );

class _ConnectionSheet extends ConsumerStatefulWidget {
  const _ConnectionSheet({required this.openModels});
  final bool openModels;

  @override
  ConsumerState<_ConnectionSheet> createState() => _ConnectionSheetState();
}

class _ConnectionSheetState extends ConsumerState<_ConnectionSheet> {
  late bool choosingModel = widget.openModels;
  bool keyVisible = false;
  String search = '';

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final controller = ref.read(chatProvider.notifier);
    final media = MediaQuery.of(context);
    final availableHeight = media.size.height - media.viewInsets.bottom;
    final filteredModels = state.models
        .where(
          (model) => '${model.name} ${model.id}'
              .toLowerCase()
              .contains(search.toLowerCase()),
        )
        .toList();

    return Padding(
      padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
      child: Glass(
        radius: 30,
        blur: 7,
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: (availableHeight * .9).clamp(390.0, 760.0),
            child: Column(
              children: [
                const _SheetHandle(),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              choosingModel ? 'Выбор модели' : 'Подключение',
                              style: const TextStyle(
                                fontSize: 24,
                                fontWeight: FontWeight.w800,
                                letterSpacing: -.6,
                              ),
                            ),
                            Text(
                              state.provider.name,
                              style: const TextStyle(color: muted, fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                      if (choosingModel)
                        RoundButton(
                          icon: Icons.arrow_back_rounded,
                          onTap: () => setState(() => choosingModel = false),
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 180),
                    child: choosingModel
                        ? _ModelPicker(
                            key: const ValueKey('models'),
                            state: state,
                            models: filteredModels,
                            onSearch: (value) => setState(() => search = value),
                            onSelect: (model) {
                              controller.model(model.id);
                              Navigator.pop(context);
                            },
                            onRefresh: () => controller.loadModels(),
                          )
                        : _ConnectionForm(
                            key: const ValueKey('connection'),
                            state: state,
                            controller: controller,
                            keyVisible: keyVisible,
                            toggleKey: () => setState(() => keyVisible = !keyVisible),
                            selectProvider: controller.provider,
                            openModels: () async {
                              if (state.models.isEmpty) {
                                final loaded = await controller.loadModels();
                                if (!loaded || !mounted) return;
                              }
                              setState(() => choosingModel = true);
                            },
                          ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SheetHandle extends StatelessWidget {
  const _SheetHandle();
  @override
  Widget build(BuildContext context) => Center(
        child: Container(
          width: 38,
          height: 4,
          margin: const EdgeInsets.only(top: 10, bottom: 18),
          decoration: BoxDecoration(
            color: const Color(0xFF596675),
            borderRadius: BorderRadius.circular(4),
          ),
        ),
      );
}

class _ConnectionForm extends StatelessWidget {
  const _ConnectionForm({
    super.key,
    required this.state,
    required this.controller,
    required this.keyVisible,
    required this.toggleKey,
    required this.selectProvider,
    required this.openModels,
  });

  final ChatState state;
  final ChatController controller;
  final bool keyVisible;
  final VoidCallback toggleKey;
  final ValueChanged<AiProvider> selectProvider;
  final VoidCallback openModels;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 28),
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        children: [
          const _Label('Провайдер'),
          const SizedBox(height: 8),
          SizedBox(
            height: 74,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: providers.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final provider = providers[index];
                final selected = provider.id == state.provider.id;
                return Semantics(
                  button: true,
                  selected: selected,
                  label: provider.name,
                  child: Material(
                    key: ValueKey('provider-${provider.id}'),
                    color: selected
                        ? const Color(0xFF243542)
                        : const Color(0xCC141A23),
                    borderRadius: BorderRadius.circular(16),
                    child: InkWell(
                      onTap: () => selectProvider(provider),
                      borderRadius: BorderRadius.circular(16),
                      child: Container(
                        width: 92,
                        padding: const EdgeInsets.symmetric(horizontal: 10),
                        decoration: BoxDecoration(
                          border: Border.all(
                            color: selected ? const Color(0xFF54788B) : line,
                          ),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            ProviderBadge(provider, size: 30),
                            const SizedBox(height: 6),
                            Text(
                              provider.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 9,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 18),
          const _Label('API-ключ'),
          const SizedBox(height: 8),
          TextFormField(
            key: ValueKey('api-key-${state.provider.id}'),
            initialValue: state.key,
            onChanged: controller.apiKey,
            obscureText: !keyVisible,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              hintText: state.provider.keyHint,
              suffixIcon: IconButton(
                onPressed: toggleKey,
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
            const SizedBox(height: 16),
            const _Label('Base URL'),
            const SizedBox(height: 8),
            TextFormField(
              key: const ValueKey('base-url'),
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
            height: 54,
            child: FilledButton.icon(
              key: const ValueKey('load-models'),
              onPressed: state.key.isEmpty || state.loadingModels
                  ? null
                  : openModels,
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF294353),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              icon: state.loadingModels
                  ? const SizedBox.square(
                      dimension: 17,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.grid_view_rounded),
              label: Text(
                state.models.isEmpty
                    ? 'Загрузить и выбрать модель'
                    : 'Выбрать модель (${state.models.length})',
              ),
            ),
          ),
          if (state.model.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              state.model,
              textAlign: TextAlign.center,
              style: const TextStyle(color: accent, fontSize: 10),
            ),
          ],
          const SizedBox(height: 18),
          const _Label('Системная инструкция'),
          const SizedBox(height: 8),
          TextFormField(
            initialValue: state.system,
            onChanged: controller.system,
            minLines: 3,
            maxLines: 4,
            decoration: const InputDecoration(
              hintText: 'Как должен отвечать ассистент?',
            ),
          ),
          const SizedBox(height: 16),
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
      );
}

class _ModelPicker extends StatelessWidget {
  const _ModelPicker({
    super.key,
    required this.state,
    required this.models,
    required this.onSearch,
    required this.onSelect,
    required this.onRefresh,
  });

  final ChatState state;
  final List<AiModel> models;
  final ValueChanged<String> onSearch;
  final ValueChanged<AiModel> onSelect;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const ValueKey('model-search'),
                    autofocus: true,
                    onChanged: onSearch,
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
                  onTap: onRefresh,
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
                final selected = model.id == state.model;
                return Material(
                  color: selected
                      ? const Color(0xFF20313D)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                  child: InkWell(
                    onTap: () => onSelect(model),
                    borderRadius: BorderRadius.circular(14),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 13,
                        vertical: 12,
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
                          if (selected)
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
      );
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
