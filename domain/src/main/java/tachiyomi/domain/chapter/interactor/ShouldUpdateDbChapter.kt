package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter

class ShouldUpdateDbChapter {

    fun await(dbChapter: Chapter, sourceChapter: Chapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            dbChapter.chapterNumber != sourceChapter.chapterNumber ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder ||
            // AY -->
            dbChapter.summary != sourceChapter.summary ||
            dbChapter.fillermark != sourceChapter.fillermark ||
            dbChapter.previewUrl != sourceChapter.previewUrl
        // <-- AY
    }
}
