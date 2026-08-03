import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nitronbox/core/chat_state.dart';
import 'package:nitronbox/main.dart';

void main() {
  testWidgets('provider and API key update without nested sheet navigation', (tester) async {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const NitronBoxApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.tune_rounded));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('provider-anthropic')));
    await tester.pumpAndSettle();

    expect(container.read(chatProvider).provider.id, 'anthropic');
    expect(find.text('Подключение'), findsOneWidget);

    final keyField = find.byKey(const ValueKey('api-key-anthropic'));
    expect(keyField, findsOneWidget);
    await tester.enterText(keyField, 'sk-ant-test-key');
    await tester.pump();

    expect(container.read(chatProvider).key, 'sk-ant-test-key');
    expect(find.text('Подключение'), findsOneWidget);
  });
}
