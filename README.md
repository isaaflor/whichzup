# WhichZup 📱💬

Um aplicativo Android de mensagens em tempo real projetado para oferecer uma comunicação rápida e fluida. O projeto foi desenvolvido com foco em uma interface moderna e reativa, além de uma arquitetura limpa e escalável.

## 👥 Equipe de Desenvolvimento

* Arthur Martins Aguiar
* Eduardo Lordão Oliveira
* Gabriel Fernandes da Silva
* Isabelle Alves Florêncio

---

## 🚀 Tecnologias e Arquitetura

Este projeto foi construído utilizando as melhores práticas e ferramentas do ecossistema de desenvolvimento Android moderno:

* **Linguagem:** Kotlin
* **Interface de Usuário:** Jetpack Compose para uma UI declarativa e reativa.
* **Padrão de Arquitetura:** MVVM (Model-View-ViewModel) para separação clara de responsabilidades e facilidade de testes.
* **Backend as a Service:** Firebase
    * *Firebase Authentication:* Para gerenciamento seguro de login e usuários.
    * *Firebase Realtime Database / Cloud Firestore:* Para envio e recebimento de mensagens em tempo real.

## ✨ Funcionalidades Principais

* **Mensageria em Tempo Real:** Envio e recebimento instantâneo de mensagens.
* **Autenticação de Usuários:** Sistema seguro de cadastro e login.
* **Interface Moderna:** Design responsivo e fluido criado com Jetpack Compose.
* *(Adicione outras funcionalidades aqui, como "Envio de imagens", "Grupos", "Status de online", etc.)*

## 🛠️ Como executar o projeto

### Pré-requisitos
* [Android Studio](https://developer.android.com/studio) (Versão mais recente recomendada)
* Conta no [Firebase Console](https://console.firebase.google.com/)

### Passo a Passo

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/seu-usuario/WhichZup.git](https://github.com/seu-usuario/WhichZup.git)
    ```
2.  **Abra o projeto:** Inicie o Android Studio e selecione `Open an existing project`, navegando até a pasta clonada.
3.  **Configuração do Firebase:**
    * Crie um projeto no Firebase Console.
    * Registre um aplicativo Android com o mesmo *package name* do WhichZup.
    * Faça o download do arquivo `google-services.json` e coloque-o na pasta `app/` do seu projeto.
    * Ative os serviços de Autenticação e o Banco de Dados no Firebase Console.
4.  **Execute o App:** Conecte um emulador ou dispositivo físico e clique em `Run` (Shift + F10) no Android Studio.

## 🎓 Agradecimentos

Gostaríamos de expressar nossos sinceros agradecimentos ao **Professor Doutor Alexsandro Santos Soares** por todo o apoio, orientação e conhecimentos valiosos compartilhados durante o desenvolvimento deste projeto para o curso de Sistemas de Informação.

## 🤝 Contribuição

Sinta-se à vontade para fazer um *fork* do projeto, abrir *issues* ou enviar *pull requests* com melhorias.

