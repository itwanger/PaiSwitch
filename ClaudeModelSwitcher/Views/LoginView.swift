import SwiftUI

struct LoginView: View {
    @StateObject private var authManager = AuthManager.shared
    @State private var isLogin = true
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""
    @State private var serverURL = ""

    var body: some View {
        VStack(spacing: 24) {
            // Header
            VStack(spacing: 8) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 48))
                    .foregroundColor(.accentColor)
                Text("PaiSwitch")
                    .font(.title)
                    .fontWeight(.bold)
                Text("AI 模型切换工具")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .padding(.bottom, 16)

            // Server URL
            VStack(alignment: .leading, spacing: 6) {
                Text("服务器地址")
                    .font(.caption)
                    .foregroundColor(.secondary)
                TextField("http://localhost:8080/api/v1", text: $serverURL)
                    .textFieldStyle(.roundedBorder)
                    .onAppear {
                        serverURL = authManager.getServerURL()
                    }
                    .onChange(of: serverURL) { _, newValue in
                        authManager.setServerURL(newValue)
                    }
            }

            // Form
            VStack(spacing: 16) {
                TextField("用户名", text: $username)
                    .textFieldStyle(.roundedBorder)

                if !isLogin {
                    TextField("邮箱", text: $email)
                        .textFieldStyle(.roundedBorder)
                        .textContentType(.emailAddress)
                }

                SecureField("密码", text: $password)
                    .textFieldStyle(.roundedBorder)
            }

            // Error
            if let error = authManager.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .font(.caption)
            }

            // Buttons
            VStack(spacing: 12) {
                Button {
                    Task {
                        if isLogin {
                            await authManager.login(username: username, password: password)
                        } else {
                            await authManager.register(username: username, email: email, password: password)
                        }
                    }
                } label: {
                    if authManager.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(isLogin ? "登录" : "注册")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(username.isEmpty || password.isEmpty || (!isLogin && email.isEmpty))

                Button {
                    withAnimation {
                        isLogin.toggle()
                    }
                } label: {
                    Text(isLogin ? "没有账号？立即注册" : "已有账号？立即登录")
                        .font(.caption)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(32)
        .frame(width: 360)
    }
}

#Preview {
    LoginView()
}
