.text
main:
  addi $sp, $sp, -72
  sw $ra, 68($sp)
  sw $fp, 64($sp)
  addi $fp, $sp, 64
  li $t1, 0
  sw $t1, -24($fp)
  li $t1, 2
  sw $t1, -12($fp)
  li $t1, 3
  sw $t1, -16($fp)
  li $t1, 6
  sw $t1, -20($fp)
  li $t1, 0
  sw $t1, -56($fp)
  li $v0, 5
  syscall
  lw $t2, -28($fp)
  move $t2, $v0
  sw $t2, -28($fp)
  li $t9, 1
  lw $t2, -28($fp)
  bgt $t2, $t9, label0_main
  li $t1, 0
  sw $t1, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  j print_main
label0_main:
  li $t9, 3
  lw $t2, -28($fp)
  bgt $t2, $t9, label1_main
  li $t1, 1
  sw $t1, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  j print_main
label1_main:
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t1, -12($fp)
  move $a1, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  lw $t2, -40($fp)
  move $t2, $v0
  sw $t2, -40($fp)
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t1, -56($fp)
  lw $t2, -32($fp)
  move $t2, $t1
  sw $t2, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  li $t9, 1
  lw $t2, -40($fp)
  beq $t2, $t9, label2_main
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t1, -16($fp)
  move $a1, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  lw $t2, -40($fp)
  move $t2, $v0
  sw $t2, -40($fp)
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t1, -56($fp)
  lw $t2, -32($fp)
  move $t2, $t1
  sw $t2, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  li $t9, 1
  lw $t2, -40($fp)
  beq $t2, $t9, label2_main
  j label3_main
label2_main:
  j print_main
label3_main:
  li $t1, 5
  sw $t1, -24($fp)
loop_main:
  lw $t1, -36($fp)
  lw $t2, -24($fp)
  lw $t3, -24($fp)
  mul $t1, $t2, $t3
  sw $t1, -36($fp)
  lw $t1, -28($fp)
  lw $t2, -36($fp)
  bgt $t2, $t1, exit_main
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t1, -24($fp)
  move $a1, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  lw $t2, -40($fp)
  move $t2, $v0
  sw $t2, -40($fp)
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t1, -56($fp)
  lw $t2, -32($fp)
  move $t2, $t1
  sw $t2, -32($fp)
  li $t1, 0
  sw $t1, -48($fp)
  li $t1, 0
  sw $t1, -60($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  li $t9, 1
  lw $t2, -40($fp)
  beq $t2, $t9, label2_main
  lw $t1, -24($fp)
  lw $t2, -44($fp)
  addi $t2, $t1, 2
  sw $t2, -44($fp)
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t1, -44($fp)
  move $a1, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  lw $t2, -40($fp)
  move $t2, $v0
  sw $t2, -40($fp)
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t1, -56($fp)
  lw $t2, -32($fp)
  move $t2, $t1
  sw $t2, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
  li $t9, 1
  lw $t2, -40($fp)
  beq $t2, $t9, label2_main
  lw $t1, -24($fp)
  addi $t1, $t1, 6
  sw $t1, -24($fp)
  j loop_main
exit_main:
  lw $t1, -48($fp)
  lw $t2, -52($fp)
  move $t2, $t1
  sw $t2, -52($fp)
  lw $t1, -60($fp)
  lw $t2, -32($fp)
  move $t2, $t1
  sw $t2, -32($fp)
  li $t1, 1
  sw $t1, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -8($fp)
  move $t2, $t1
  sw $t2, -8($fp)
print_main:
  lw $t1, -8($fp)
  move $a0, $t1
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  li $v0, 10
  syscall
divisible:
  addi $sp, $sp, -32
  sw $ra, 28($sp)
  sw $fp, 24($sp)
  addi $fp, $sp, 24
  lw $t2, -8($fp)
  move $t2, $a0
  sw $t2, -8($fp)
  lw $t2, -12($fp)
  move $t2, $a1
  sw $t2, -12($fp)
  lw $t1, -16($fp)
  lw $t2, -8($fp)
  lw $t3, -12($fp)
  div $t1, $t2, $t3
  sw $t1, -16($fp)
  lw $t1, -16($fp)
  lw $t2, -16($fp)
  lw $t3, -12($fp)
  mul $t1, $t2, $t3
  sw $t1, -16($fp)
  lw $t1, -16($fp)
  lw $t2, -8($fp)
  bne $t2, $t1, label0_divisible
  li $v0, 1
  lw $ra, 28($sp)
  lw $fp, 24($sp)
  addi $sp, $sp, 32
  jr $ra
label0_divisible:
  li $v0, 0
  lw $ra, 28($sp)
  lw $fp, 24($sp)
  addi $sp, $sp, 32
  jr $ra

