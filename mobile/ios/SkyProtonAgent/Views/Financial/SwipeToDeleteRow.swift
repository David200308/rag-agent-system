import SwiftUI

/// A leading swipe action shown before the (always-present) trailing delete action.
struct SwipeAction {
    let icon: String
    let tint: Color
    let action: () -> Void
}

/// iOS-style swipe actions for a row that isn't inside a `List` (this app builds its finance
/// rows as plain `VStack`s inside `ThemeCard`, so the native `.swipeActions` modifier — which
/// only attaches to `List` rows — isn't available). Drag left to reveal any extra `actions`
/// plus a red trash panel; tapping one calls its handler.
struct SwipeToDeleteRow<Content: View>: View {
    var actions: [SwipeAction] = []
    var onDelete: () -> Void
    @ViewBuilder var content: Content

    @State private var offset: CGFloat = 0
    @GestureState private var dragTranslation: CGFloat = 0

    private let actionWidth: CGFloat = 72
    private var totalWidth: CGFloat { actionWidth * CGFloat(actions.count + 1) }

    private var currentOffset: CGFloat {
        min(0, max(offset + dragTranslation, -totalWidth))
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            HStack(spacing: 0) {
                ForEach(Array(actions.enumerated()), id: \.offset) { _, a in
                    Button(action: a.action) {
                        Image(systemName: a.icon)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: actionWidth)
                            .frame(maxHeight: .infinity)
                    }
                    .background(a.tint)
                }
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: actionWidth)
                        .frame(maxHeight: .infinity)
                }
                .background(Color(red: 0.83, green: 0.30, blue: 0.27))
            }

            content
                .background(Theme.surface)
                .offset(x: currentOffset)
                .gesture(
                    DragGesture(minimumDistance: 12)
                        .updating($dragTranslation) { value, state, _ in
                            state = value.translation.width
                        }
                        .onEnded { value in
                            withAnimation(.easeOut(duration: 0.2)) {
                                offset = value.translation.width < -totalWidth / 2 ? -totalWidth : 0
                            }
                        }
                )
        }
    }
}
