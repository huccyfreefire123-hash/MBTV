# MBTV

Player de IPTV para celular e Android TV (listas M3U e Xtream Codes), com uma aba MBTV para assistir vídeos e músicas do YouTube, baseada no [YTXTube](https://github.com/Koustubh12345/YTXTube).

## Baixar o APK

O APK pronto para instalar fica em [Releases](../../releases/latest). No Android, permita "instalar apps desconhecidos" para o navegador ou o gerenciador de arquivos e abra o arquivo baixado.

## O que o app faz

- TV ao vivo, filmes e séries de listas M3U ou contas Xtream Codes, com guia de programação (EPG)
- Início com carrossel, "Filmes em Alta" e "Séries em Alta" (lançamentos do ano atual), continuar assistindo e favoritos
- Controle parental: a categoria adulta pede senha (PIN), criada e alterada pelo próprio usuário
- Perfis, busca, multitela e esportes
- Aba MBTV: vídeos, músicas, shorts, playlists e login na conta do YouTube
- Funciona no celular e na TV, com navegação pelo controle remoto

## Como compilar

Requisitos:

- Android Studio recente (com suporte ao Android Gradle Plugin 9.1)
- JDK 17 ou mais novo
- Android SDK Platform 37 (instale pelo SDK Manager do Android Studio)

Passos:

1. Clone o repositório: `git clone <link deste repositório>`
2. Abra a pasta no Android Studio e espere o Gradle sincronizar
3. Gere o APK pelo menu Build > Generate APKs, ou pelo terminal:
   - Windows: `gradlew.bat assembleRelease`
   - Linux/macOS: `./gradlew assembleRelease`
4. O APK fica em `app/build/outputs/apk/release/app-release.apk`

O APK de release é assinado com a chave de debug, para facilitar a instalação. Para publicar o app em uma loja, crie a sua própria chave de assinatura (Build > Generate Signed App Bundle / APK).

## Estrutura

- `app/`: o app MBTV (Kotlin e Jetpack Compose), pacote `com.ondatv.player`
- `ytxtube/`: módulo da aba MBTV, adaptado do YTXTube

## Créditos e licença

A aba MBTV usa o código do [YTXTube](https://github.com/Koustubh12345/YTXTube), licenciado sob a GNU GPL v3. Por isso este projeto também é distribuído sob a [GPL-3.0](LICENSE): você pode usar, modificar e redistribuir, desde que mantenha a mesma licença e disponibilize o código-fonte.

## Aviso

O MBTV é apenas um player. Ele não fornece canais, filmes, séries nem assinaturas: use somente listas que você tem direito de assistir.
