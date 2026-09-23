package my.noveldokusha.core.appPreferences

// Позиция полосы источника на карточке книги в библиотеке/каталоге.
// OnCover — полоса наложена на кромку обложки (sourceStripOnCover = true).
// BelowCover — полоса вынесена отдельной плашкой под обложкой (sourceStripOnCover = false).
// InfoPanel — плашка под обложкой: название источника/главы крупнее заголовка, заголовок мелко.
enum class SourceStripPosition { OnCover, BelowCover, InfoPanel }
