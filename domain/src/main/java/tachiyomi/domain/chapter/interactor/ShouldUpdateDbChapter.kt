package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter

class ShouldUpdateDbChapter {

    fun await(dbChapter: Chapter, sourceChapter: Chapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            dbChapter.episodeNumber != sourceChapter.episodeNumber ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder
    }
}
