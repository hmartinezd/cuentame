package com.venkoi.cuentame.core.model.purchase

class DuplicateInvoicePostingException(val candidate: DuplicateInvoiceCandidate) :
    IllegalStateException("Strong duplicate invoice requires explicit override")
