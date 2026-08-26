import SwiftUI

/// iOS-style swipe-to-delete for a row that isn't inside a `List` (this app builds its
/// finance rows as plain `VStack`s inside `ThemeCard`, so the native `.swipeActions`
/// modifier — which only attaches to `List` rows — isn't available). Drag left to reveal
/// a red trash panel; tapping it calls `onDelete`.
struct SwipeToDeleteRow<Content: View>: View {
    var onDelete: () -> Void
    @ViewBuilder var content: Content

    @State private var offset: CGFloat = 0
    @GestureState private var dragTranslation: CGFloat = 0

    private let actionWidth: CGFloat = 72

    private var currentOffset: CGFloat {
        min(0, max(offset + dragTranslation, -actionWidth))
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: actionWidth)
                    .frame(maxHeight: .infinity)
            }
            .background(Color(red: 0.83, green: 0.30, blue: 0.27))

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
                                offset = value.translation.width < -actionWidth / 2 ? -actionWidth : 0
                            }
                        }
                )
        }
    }
}
