import SwiftUI

struct MessageBubble: View {
    let message: ChatViewModel.ChatMessage

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if message.isUser {
                Spacer(minLength: 60)
                Text(message.content)
                    .font(.subheadline)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(.label))
                    .foregroundStyle(Color(.systemBackground))
                    .clipShape(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 18, bottomLeadingRadius: 18,
                            bottomTrailingRadius: 4, topTrailingRadius: 18
                        )
                    )
                    .textSelection(.enabled)
            } else {
                Image("Logo")
                    .resizable().scaledToFit()
                    .frame(width: 20, height: 20)
                    .colorMultiply(.primary)
                    .padding(5)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Circle())

                MarkdownBubble(content: message.content)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 4, bottomLeadingRadius: 18,
                            bottomTrailingRadius: 18, topTrailingRadius: 18
                        )
                    )

                Spacer(minLength: 60)
            }
        }
        .padding(.horizontal, 12)
    }
}

struct TypingIndicator: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Image("Logo")
                .resizable().scaledToFit()
                .frame(width: 20, height: 20)
                .colorMultiply(.primary)
                .padding(5)
                .background(Color(.secondarySystemBackground))
                .clipShape(Circle())

            HStack(spacing: 4) {
                ForEach(0..<3, id: \.self) { i in
                    Circle()
                        .frame(width: 6, height: 6)
                        .foregroundStyle(Color(.tertiaryLabel))
                        .scaleEffect(1 + 0.45 * sin(phase + CGFloat(i) * .pi * 0.65))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 18))

            Spacer()
        }
        .padding(.horizontal, 12)
        .onAppear {
            withAnimation(.linear(duration: 1.1).repeatForever(autoreverses: false)) {
                phase = .pi * 2
            }
        }
    }
}
