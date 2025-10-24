.text
main:
  addi $sp, $sp, -72
  sw $ra, 68($sp)
  sw $fp, 64($sp)
  addi $fp, $sp, 64
  li $t1, 0
  li $t2, 2
  li $t3, 3
  li $t4, 6
  sw $t2, -12($fp)
  li $t2, 0
  li $v0, 5
  sw $t3, -16($fp)
  sw $t4, -20($fp)
  sw $t1, -24($fp)
  sw $t2, -56($fp)
  syscall
  move $t1, $v0
  li $t9, 1
  sw $t1, -28($fp)
  lw $t1, -28($fp)
  bgt $t1, $t9, label0_main
  li $t1, 0
  move $t2, $t1
  sw $t2, -8($fp)
  sw $t1, -32($fp)
  j print_main
label0_main:
  li $t9, 3
  lw $t1, -28($fp)
  bgt $t1, $t9, label1_main
  li $t1, 1
  move $t2, $t1
  sw $t2, -8($fp)
  sw $t1, -32($fp)
  j print_main
label1_main:
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t2, -12($fp)
  move $a1, $t2
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $t1, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t2, -56($fp)
  move $t3, $t2
  move $t4, $t3
  li $t9, 1
  sw $t4, -8($fp)
  sw $t3, -32($fp)
  sw $t1, -40($fp)
  lw $t1, -40($fp)
  beq $t1, $t9, label2_main
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t2, -16($fp)
  move $a1, $t2
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $t1, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t2, -56($fp)
  move $t3, $t2
  move $t4, $t3
  li $t9, 1
  sw $t4, -8($fp)
  sw $t3, -32($fp)
  sw $t1, -40($fp)
  lw $t1, -40($fp)
  beq $t1, $t9, label2_main
  j label3_main
label2_main:
  j print_main
label3_main:
  li $t1, 5
  sw $t1, -24($fp)
loop_main:
  lw $t1, -24($fp)
  mul $t2, $t1, $t1
  sw $t2, -36($fp)
  lw $t1, -36($fp)
  lw $t2, -28($fp)
  bgt $t1, $t2, exit_main
  lw $t1, -28($fp)
  move $a0, $t1
  lw $t2, -24($fp)
  move $a1, $t2
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal divisible
  move $t1, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t2, -56($fp)
  move $t3, $t2
  li $t4, 0
  li $t2, 0
  sw $t3, -32($fp)
  li $t9, 1
  sw $t3, -8($fp)
  sw $t1, -40($fp)
  sw $t4, -48($fp)
  sw $t2, -60($fp)
  lw $t1, -40($fp)
  beq $t1, $t9, label2_main
  lw $t1, -24($fp)
  addi $t2, $t1, 2
  lw $t3, -28($fp)
  move $a0, $t3
  move $a1, $t2
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  sw $t2, -44($fp)
  jal divisible
  move $t1, $v0
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  lw $t2, -56($fp)
  move $t3, $t2
  move $t4, $t3
  li $t9, 1
  sw $t4, -8($fp)
  sw $t3, -32($fp)
  sw $t1, -40($fp)
  lw $t1, -40($fp)
  beq $t1, $t9, label2_main
  lw $t1, -24($fp)
  addi $t1, $t1, 6
  sw $t1, -24($fp)
  j loop_main
exit_main:
  lw $t1, -48($fp)
  move $t2, $t1
  lw $t3, -60($fp)
  move $t4, $t3
  li $t4, 1
  move $t1, $t4
  sw $t1, -8($fp)
  sw $t4, -32($fp)
  sw $t2, -52($fp)
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
  move $t1, $a0
  move $t2, $a1
  div $t3, $t1, $t2
  mul $t3, $t3, $t2
  sw $t3, -16($fp)
  sw $t1, -8($fp)
  sw $t2, -12($fp)
  lw $t1, -8($fp)
  lw $t2, -16($fp)
  bne $t1, $t2, label0_divisible
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

