.text
main:
  addi $sp, $sp, -432
  sw $ra, 428($sp)
  sw $fp, 424($sp)
  addi $fp, $sp, 424
  li $t1, 0
  sw $t1, -408($fp)
  li $v0, 5
  syscall
  lw $t2, -416($fp)
  move $t2, $v0
  sw $t2, -416($fp)
  li $t9, 100
  lw $t2, -416($fp)
  bgt $t2, $t9, return_main
  li $t9, 1
  lw $t1, -416($fp)
  lw $t2, -416($fp)
  sub $t1, $t2, $t9
  sw $t1, -416($fp)
  li $t1, 0
  sw $t1, -412($fp)
loop0_main:
  lw $t1, -416($fp)
  lw $t2, -412($fp)
  bgt $t2, $t1, exit0_main
  li $v0, 5
  syscall
  lw $t2, -408($fp)
  move $t2, $v0
  sw $t2, -408($fp)
  lw $t1, -412($fp)
  sll $t8, $t1, 2
  addi $t9, $fp, 0
  sub $t9, $t9, $t8
  lw $t1, -408($fp)
  sw $t1, 0($t9)
  lw $t1, -412($fp)
  addi $t1, $t1, 1
  sw $t1, -412($fp)
  j loop0_main
exit0_main:
  move $a0, $fp
  addi $a0, $a0, 0
  li $a1, 0
  lw $t1, -416($fp)
  move $a2, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  li $t1, 0
  sw $t1, -412($fp)
loop1_main:
  lw $t1, -416($fp)
  lw $t2, -412($fp)
  bgt $t2, $t1, exit1_main
  lw $t1, -412($fp)
  sll $t8, $t1, 2
  addi $t9, $fp, 0
  sub $t9, $t9, $t8
  lw $t1, -408($fp)
  lw $t1, 0($t9)
  sw $t1, -408($fp)
  lw $t1, -408($fp)
  move $a0, $t1
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  lw $t1, -412($fp)
  addi $t1, $t1, 1
  sw $t1, -412($fp)
  j loop1_main
exit1_main:
return_main:
  li $v0, 10
  syscall
quicksort:
  addi $sp, $sp, -64
  sw $ra, 60($sp)
  sw $fp, 56($sp)
  addi $fp, $sp, 56
  lw $t2, -8($fp)
  move $t2, $a0
  sw $t2, -8($fp)
  lw $t2, -12($fp)
  move $t2, $a1
  sw $t2, -12($fp)
  lw $t2, -16($fp)
  move $t2, $a2
  sw $t2, -16($fp)
  li $t1, 0
  sw $t1, -44($fp)
  li $t1, 0
  sw $t1, -48($fp)
  lw $t1, -16($fp)
  lw $t2, -12($fp)
  bge $t2, $t1, end_quicksort
  lw $t1, -36($fp)
  lw $t2, -12($fp)
  lw $t3, -16($fp)
  add $t1, $t2, $t3
  sw $t1, -36($fp)
  li $t9, 2
  lw $t1, -36($fp)
  lw $t2, -36($fp)
  div $t1, $t2, $t9
  sw $t1, -36($fp)
  lw $t1, -36($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t1, -40($fp)
  lw $t1, 0($t9)
  sw $t1, -40($fp)
  li $t9, 1
  lw $t1, -44($fp)
  lw $t2, -12($fp)
  sub $t1, $t2, $t9
  sw $t1, -44($fp)
  lw $t1, -16($fp)
  lw $t2, -48($fp)
  addi $t2, $t1, 1
  sw $t2, -48($fp)
loop0_quicksort:
loop1_quicksort:
  lw $t1, -44($fp)
  addi $t1, $t1, 1
  sw $t1, -44($fp)
  lw $t1, -44($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t1, -32($fp)
  lw $t1, 0($t9)
  sw $t1, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -20($fp)
  move $t2, $t1
  sw $t2, -20($fp)
  lw $t1, -40($fp)
  lw $t2, -20($fp)
  blt $t2, $t1, loop1_quicksort
loop2_quicksort:
  li $t9, 1
  lw $t1, -48($fp)
  lw $t2, -48($fp)
  sub $t1, $t2, $t9
  sw $t1, -48($fp)
  lw $t1, -48($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t1, -32($fp)
  lw $t1, 0($t9)
  sw $t1, -32($fp)
  lw $t1, -32($fp)
  lw $t2, -24($fp)
  move $t2, $t1
  sw $t2, -24($fp)
  lw $t1, -40($fp)
  lw $t2, -24($fp)
  bgt $t2, $t1, loop2_quicksort
  lw $t1, -48($fp)
  lw $t2, -44($fp)
  bge $t2, $t1, exit0_quicksort
  lw $t1, -48($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t1, -20($fp)
  sw $t1, 0($t9)
  lw $t1, -44($fp)
  sll $t8, $t1, 2
  lw $t2, -8($fp)
  sub $t9, $t2, $t8
  lw $t1, -24($fp)
  sw $t1, 0($t9)
  j loop0_quicksort
exit0_quicksort:
  lw $t1, -48($fp)
  lw $t2, -28($fp)
  addi $t2, $t1, 1
  sw $t2, -28($fp)
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  lw $t1, -8($fp)
  move $a0, $t1
  lw $t1, -12($fp)
  move $a1, $t1
  lw $t1, -48($fp)
  move $a2, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
  lw $t1, -48($fp)
  addi $t1, $t1, 1
  sw $t1, -48($fp)
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  lw $t1, -8($fp)
  move $a0, $t1
  lw $t1, -48($fp)
  move $a1, $t1
  lw $t1, -16($fp)
  move $a2, $t1
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
end_quicksort:
  lw $ra, 60($sp)
  lw $fp, 56($sp)
  addi $sp, $sp, 64
  jr $ra

