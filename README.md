🎨 Painter – Android Drawing App (Jetpack Compose)

Jetpack Compose와 Kotlin을 이용해 구현한 안드로이드 그림판 애플리케이션입니다.
안드로이드 앱 개발 실습 과제로 제작되었으며, Canvas 기반 드로잉과 상태 관리를 중심으로 구현했습니다.

📌 핵심 기능 요약

Jetpack Compose Canvas 기반 그림판

6종 브러시 지원

색상 / 크기 / 투명도 조절

멀티 터치 줌 & 이동

레이어 기능

Undo / Redo

타임랩스 재생

저장 / 불러오기 / PNG 내보내기

✏️ 브러시 기능
🔹 지원 브러시

연필

볼펜

붓

형광펜

에어브러시

지우개



브러시 종류에 따라 선 두께, 투명도, 표현 방식이 달라집니다.

🎨 색상 · 크기 · 투명도 조절


색상 팔레트 선택

슬라이더로 선 두께 조절

슬라이더로 투명도 조절

🔍 줌 인 / 줌 아웃 & 캔버스 이동

두 손가락으로 확대 / 축소

캔버스 자유 이동 가능

한 손가락 드로잉과 제스처 충돌 없이 처리

🗂️ 레이어 기능
<img width="1080" height="721" alt="image" src="https://github.com/user-attachments/assets/ac837b7b-01a3-4eda-b12f-9a4090bf3d5b" />

2개 레이어 지원

레이어 선택

레이어 표시 / 숨김

↩️ Undo / Redo
<img src="screenshots/undo_redo.png" width="300"/>

드로잉 작업 실행 취소

다시 실행 가능

스트로크 단위로 관리

⏱️ 타임랩스 재생
<img src="screenshots/timelapse.png" width="300"/>

그림 그리는 과정을 순서대로 재생

재생 / 일시정지 / 되감기 지원

속도 조절 가능

💾 저장 · 불러오기 · 내보내기
<img src="screenshots/save_export.png" width="300"/>

프로젝트 저장 / 불러오기 (JSON)

완성된 그림을 PNG 이미지로 갤러리에 저장

🛠️ 사용 기술

Language: Kotlin

UI: Jetpack Compose

Graphics: Canvas API

Architecture: Single Activity + ViewModel

Data: Gson (JSON 직렬화)

Build: Gradle (Kotlin DSL)
