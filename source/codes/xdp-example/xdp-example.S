	.text
	.file	"xdp-example.c"
	.section	prog,"ax",@progbits
	.globl	xdp_pass                        # -- Begin function xdp_pass
	.p2align	3
	.type	xdp_pass,@function
xdp_pass:                               # @xdp_pass
# %bb.0:
	r0 = 2
	exit
.Lfunc_end0:
	.size	xdp_pass, .Lfunc_end0-xdp_pass
                                        # -- End function
	.type	__license,@object               # @__license
	.section	license,"aw",@progbits
	.globl	__license
__license:
	.asciz	"GPL"
	.size	__license, 4

	.addrsig
	.addrsig_sym xdp_pass
	.addrsig_sym __license
